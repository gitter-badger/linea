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
package com.srotya.linea.topology;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple test topology to validate how Linea will launch and run pipelines and
 * acking.
 * 
 * Fixed bugs with copy translator causing issues in acking.
 * 
 * @author ambud
 */
public class SimpleTopology {

	public static void main(String[] args) throws Exception {
		Map<String, String> conf = new HashMap<>();
		conf.put(TopologyBuilder.WORKER_COUNT, "1");
		conf.put(TopologyBuilder.ACKER_PARALLELISM, "6");
		TopologyBuilder builder = new TopologyBuilder(conf);
		builder = builder.addSpout(new TestSpout(), 8).addBolt(new PrinterBolt(), 6).start();
		Thread.sleep(50000);
		System.exit(1);
	}
}
