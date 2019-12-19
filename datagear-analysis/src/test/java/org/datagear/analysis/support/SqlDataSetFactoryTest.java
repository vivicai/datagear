/*
 * Copyright (c) 2018 datagear.tech. All Rights Reserved.
 */

/**
 * 
 */
package org.datagear.analysis.support;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.datagear.analysis.ColumnMeta;
import org.datagear.analysis.DataSet;
import org.datagear.analysis.DataSetMeta;
import org.datagear.analysis.DataSetParam;
import org.datagear.analysis.DataSetParamValues;
import org.datagear.analysis.DataSetParams;
import org.datagear.analysis.DataType;
import org.datagear.util.JdbcUtil;
import org.datagear.util.resource.SimpleConnectionFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@linkplain SqlDataSetFactory}单元测试类。
 * 
 * @author datagear@163.com
 *
 */
public class SqlDataSetFactoryTest extends DBTestSupport
{
	@Test
	public void getDataSetTest() throws Exception
	{
		Connection cn = null;

		String[] columnLabels = { "标识", "名称" };
		long recordId = 999999999;
		String recordName = SqlDataSetFactory.class.getSimpleName();

		try
		{
			cn = getConnection();
			SimpleConnectionFactory connectionFactory = new SimpleConnectionFactory(cn, false);

			{
				String insertSql = "INSERT INTO T_ACCOUNT(ID, NAME) VALUES(" + recordId + ", '" + recordName + "')";
				Statement st = null;

				try
				{
					st = cn.createStatement();
					st.executeUpdate(insertSql);
				}
				finally
				{
					JdbcUtil.closeStatement(st);
				}
			}

			String sql = "SELECT ID, NAME FROM T_ACCOUNT WHERE ID = ${id} AND NAME != ${name}";

			DataSetParams dataSetParams = new DataSetParams();
			dataSetParams.add(new DataSetParam("id", DataType.INTEGER, true));
			dataSetParams.add(new DataSetParam("name", DataType.STRING, true));

			SqlDataSetFactory sqlDataSetFactory = new SqlDataSetFactory("1", dataSetParams, connectionFactory, sql);
			sqlDataSetFactory.setColumnLabels(columnLabels);

			DataSetParamValues dataSetParamValues = new DataSetParamValues();
			dataSetParamValues.put("id", recordId);
			dataSetParamValues.put("name", "name-for-test");

			DataSet dataSet = sqlDataSetFactory.getDataSet(dataSetParamValues);

			DataSetMeta dataSetMeta = dataSet.getMeta();
			List<ColumnMeta> columnMetas = dataSetMeta.getColumnMetas();

			Assert.assertEquals(2, columnMetas.size());

			{
				ColumnMeta columnMeta = columnMetas.get(0);

				Assert.assertEquals("ID", columnMeta.getName());
				Assert.assertEquals(DataType.INTEGER, columnMeta.getDataType());
				Assert.assertEquals(columnLabels[0], columnMeta.getLabel());
			}

			{
				ColumnMeta columnMeta = columnMetas.get(1);

				Assert.assertEquals("NAME", columnMeta.getName());
				Assert.assertEquals(DataType.STRING, columnMeta.getDataType());
				Assert.assertEquals(columnLabels[1], columnMeta.getLabel());
			}

			List<Map<String, ?>> datas = dataSet.getDatas();

			Assert.assertEquals(1, datas.size());

			{
				Map<String, ?> row = datas.get(0);

				Assert.assertEquals(2, row.size());
				Assert.assertEquals(Long.toString(recordId), row.get("ID").toString());
				Assert.assertEquals(recordName, row.get("NAME"));
			}
		}
		finally
		{
			{
				String insertSql = "DELETE FROM T_ACCOUNT WHERE ID=" + recordId;
				Statement st = null;

				try
				{
					st = cn.createStatement();
					st.executeUpdate(insertSql);
				}
				finally
				{
					JdbcUtil.closeStatement(st);
				}
			}

			JdbcUtil.closeConnection(cn);
		}
	}
}