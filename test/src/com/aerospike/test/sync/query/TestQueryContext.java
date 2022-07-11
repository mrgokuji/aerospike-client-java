/*
 * Copyright 2012-2022 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.test.sync.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.ResultCode;
import com.aerospike.client.cdt.CTX;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.IndexCollectionType;
import com.aerospike.client.query.IndexType;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.aerospike.client.task.IndexTask;
import com.aerospike.test.sync.TestSync;

public class TestQueryContext extends TestSync {
	private static final String indexName = "listrank";
	private static final String binName = "list";
	private static final int size = 50;

	@BeforeClass
	public static void prepare() {
		Policy policy = new Policy();

		try {
			IndexTask task = client.createIndex(
				policy, args.namespace, args.set, indexName, binName,
				IndexType.NUMERIC, IndexCollectionType.DEFAULT,
				CTX.listRank(-1)
				);
			task.waitTillComplete();
		}
		catch (AerospikeException ae) {
			if (ae.getResultCode() != ResultCode.INDEX_ALREADY_EXISTS) {
				throw ae;
			}
		}

		for (int i = 1; i <= size; i++) {
			Key key = new Key(args.namespace, args.set, i);

			ArrayList<Integer> list = new ArrayList<Integer>(5);
			list.add(i);
			list.add(i + 1);
			list.add(i + 2);
			list.add(i + 3);
			list.add(i + 4);

			Bin bin = new Bin(binName, list);
			client.put(null, key, bin);
		}
	}

	@AfterClass
	public static void destroy() {
		client.dropIndex(null, args.namespace, args.set, indexName);
	}

	@Test
	public void queryContext() {
		long begin = 14;
		long end = 18;

		Statement stmt = new Statement();
		stmt.setNamespace(args.namespace);
		stmt.setSetName(args.set);
		stmt.setBinNames(binName);
		stmt.setFilter(Filter.range(binName, begin, end, CTX.listRank(-1)));

		RecordSet rs = client.query(null, stmt);

		try {
			int count = 0;

			while (rs.next()) {
				Record r = rs.getRecord();
				//System.out.println(r);

				List<?> list = r.getList(binName);
				long received = (Long)list.get(list.size() - 1);

				if (received < begin || received > end) {
					fail("Received not between: " + begin + " and " + end);
				}
				count++;
			}
			assertEquals(5, count);
		}
		finally {
			rs.close();
		}
	}
}
