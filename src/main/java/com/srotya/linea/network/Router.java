/**
 * Copyright 2016 Ambud Sharma
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.srotya.linea.network;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.srotya.linea.Event;
import com.srotya.linea.MurmurHash;
import com.srotya.linea.clustering.Columbus;
import com.srotya.linea.disruptor.CopyTranslator;
import com.srotya.linea.disruptor.ROUTING_TYPE;
import com.srotya.linea.processors.BoltExecutor;
import com.srotya.linea.processors.DisruptorUnifiedFactory;
import com.srotya.linea.utils.Constants;

/**
 * {@link Event} router that is responsible for sending messages across
 * instances and workers in a topology.
 * 
 * @author ambud
 */
public class Router {

	private static final Logger logger = Logger.getLogger(Router.class.getName());
	private Disruptor<Event> networkTranmissionDisruptor;
	private DisruptorUnifiedFactory factory;
	private Map<String, BoltExecutor> executorMap;
	private CopyTranslator translator;
	private Columbus columbus;
	private InternalUDPTransportServer server;
	private int workerCount;
	private ExecutorService pool;

	/**
	 * @param factory
	 * @param columbus
	 * @param workerCount
	 * @param executorMap
	 */
	public Router(DisruptorUnifiedFactory factory, Columbus columbus, int workerCount,
			Map<String, BoltExecutor> executorMap) {
		this.factory = factory;
		this.columbus = columbus;
		this.workerCount = workerCount;
		this.executorMap = executorMap;
	}

	/**
	 * Start {@link Router}
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public void start() throws Exception {
		while (columbus.getWorkerCount() < workerCount) {
			Thread.sleep(2000);
			logger.info("Waiting for worker discovery");
		}
		pool = Executors.newFixedThreadPool(2);
		server = new InternalUDPTransportServer(this,
				columbus.getWorkerMap().get(columbus.getSelfWorkerId()).getDataPort());
		pool.submit(() -> {
			try {
				server.start();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

		networkTranmissionDisruptor = new Disruptor<Event>(factory, 1024, pool, ProducerType.MULTI,
				new BlockingWaitStrategy());
		InternalUDPTransportClient client = new InternalUDPTransportClient(columbus,
				columbus.getWorkerMap().get(columbus.getSelfWorkerId()).getDataPort() + 1000, false);
		client.init();
		networkTranmissionDisruptor.handleEventsWith(client);
		networkTranmissionDisruptor.start();
		translator = new CopyTranslator();
	}

	/**
	 * Stop {@link Router}
	 * 
	 * @throws Exception
	 */
	public void stop() throws Exception {
		server.stop();
		networkTranmissionDisruptor.shutdown();
		pool.shutdownNow();
	}

	/**
	 * Direct local routing
	 * 
	 * @param nextProcessorId
	 * @param taskId
	 * @param event
	 */
	public void directLocalRouteEvent(String nextProcessorId, int taskId, Event event) {
		executorMap.get(nextProcessorId).process(taskId, event);
	}

	/**
	 * {@link Router} method called for {@link Event} routing. This method uses
	 * {@link ROUTING_TYPE} for the nextProcessorId to fetch the
	 * {@link BoltExecutor} and get the taskId to route the message to.
	 * 
	 * @param nextBoltId
	 * @param event
	 */
	public void routeEvent(String nextBoltId, Event event) {
		BoltExecutor nextBolt = executorMap.get(nextBoltId);
		if (nextBolt == null) {
			// drop this event
			System.err.println("Next bolt null, droping event:" + event);
			return;
		}
		int taskId = -1;
		int workerCount = columbus.getWorkerCount();

		// normalize parallelism
		int totalParallelism = nextBolt.getParallelism(); // get
																// local
																// parallelism
		totalParallelism = workerCount * totalParallelism;
		switch (nextBolt.getTemplateBoltInstance().getRoutingType()) {
		case GROUPBY:
			Object key = event.getHeaders().get(Constants.FIELD_GROUPBY_ROUTING_KEY);
			if (key != null) {
				taskId = (Math.abs(MurmurHash.hash32(key.toString())) % totalParallelism);
			} else {
				// discard event
			}
			break;
		case SHUFFLE:
			// taskId = columbus.getSelfWorkerId() * totalParallelism
			// + (Math.abs((int) (event.getEventId() % totalParallelism)));
			taskId = Math.abs((int) (event.getEventId() % totalParallelism));
			break;
		}

		// check if this taskId is local to this worker
		routeToTaskId(nextBoltId, event, nextBolt, taskId);
	}

	/**
	 * Used to either local or network route an event based on worker id.
	 * 
	 * @param nextBoltId
	 * @param event
	 * @param nextBolt
	 * @param taskId
	 */
	public void routeToTaskId(String nextBoltId, Event event, BoltExecutor nextBolt, int taskId) {
		if (nextBolt == null) {
			nextBolt = executorMap.get(nextBoltId);
		}
		int destinationWorker = 0;
		if (taskId >= nextBolt.getParallelism()) {
			destinationWorker = taskId / nextBolt.getParallelism();
		}

		if (destinationWorker == columbus.getSelfWorkerId()) {
			nextBolt.process(taskId, event);
		} else {
			logger.info("Network routing");
			event.getHeaders().put(Constants.FIELD_NEXT_BOLT, nextBoltId);
			event.getHeaders().put(Constants.FIELD_DESTINATION_TASK_ID, taskId);
			event.getHeaders().put(Constants.FIELD_DESTINATION_WORKER_ID, destinationWorker);
			networkTranmissionDisruptor.publishEvent(translator, event);
		}
	}

	/**
	 * @return columbus
	 */
	public Columbus getColumbus() {
		return columbus;
	}

}
