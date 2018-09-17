/*
 * Copyright (c) 2018 by datagear.org.
 */

package org.datagear.persistence.support;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.datagear.model.Model;
import org.datagear.model.Property;
import org.datagear.model.features.NotEditable;
import org.datagear.model.features.NotReadable;
import org.datagear.model.support.MU;
import org.datagear.model.support.PropertyModel;
import org.datagear.persistence.Dialect;
import org.datagear.persistence.SqlBuilder;
import org.datagear.persistence.features.KeyRule;
import org.datagear.persistence.mapper.JoinTableMapper;
import org.datagear.persistence.mapper.ModelTableMapper;
import org.datagear.persistence.mapper.PropertyModelMapper;
import org.datagear.persistence.mapper.PropertyTableMapper;
import org.datagear.persistence.mapper.RelationMapper;
import org.springframework.core.convert.ConversionService;

/**
 * 更新持久化操作类。
 * 
 * @author datagear@163.com
 *
 */
public class UpdatePersistenceOperation extends AbstractModelPersistenceOperation
{
	/** 当记录未做修改时，返回此标识 */
	public static final int UNCHANGED = PERSISTENCE_IGNORED - 1;

	/** 是否处理多元属性 */
	private boolean handleMultipleProperty = false;

	private InsertPersistenceOperation insertPersistenceOperation;

	private DeletePersistenceOperation deletePersistenceOperation;

	private ConversionService conversionService;

	public UpdatePersistenceOperation()
	{
		super();
	}

	public UpdatePersistenceOperation(InsertPersistenceOperation insertPersistenceOperation,
			DeletePersistenceOperation deletePersistenceOperation, ConversionService conversionService)
	{
		super();
		this.insertPersistenceOperation = insertPersistenceOperation;
		this.deletePersistenceOperation = deletePersistenceOperation;
		this.conversionService = conversionService;
	}

	public boolean isHandleMultipleProperty()
	{
		return handleMultipleProperty;
	}

	public void setHandleMultipleProperty(boolean handleMultipleProperty)
	{
		this.handleMultipleProperty = handleMultipleProperty;
	}

	public InsertPersistenceOperation getInsertPersistenceOperation()
	{
		return insertPersistenceOperation;
	}

	public void setInsertPersistenceOperation(InsertPersistenceOperation insertPersistenceOperation)
	{
		this.insertPersistenceOperation = insertPersistenceOperation;
	}

	public DeletePersistenceOperation getDeletePersistenceOperation()
	{
		return deletePersistenceOperation;
	}

	public void setDeletePersistenceOperation(DeletePersistenceOperation deletePersistenceOperation)
	{
		this.deletePersistenceOperation = deletePersistenceOperation;
	}

	public ConversionService getConversionService()
	{
		return conversionService;
	}

	public void setConversionService(ConversionService conversionService)
	{
		this.conversionService = conversionService;
	}

	/**
	 * 更新。
	 * 
	 * @param cn
	 * @param dialect
	 * @param table
	 * @param model
	 * @param originalCondition
	 *            用于确定原始数据记录的模型表条件
	 * @param originalObj
	 *            原始数据
	 * @param updateObj
	 *            待更新的数据
	 * @return
	 */
	public int update(Connection cn, Dialect dialect, String table, Model model, Object originalObj, Object updateObj)
	{
		SqlBuilder originalCondition = buildRecordCondition(cn, dialect, model, originalObj, null);

		return update(cn, dialect, table, model, originalCondition, originalObj, updateObj, null, null, null,
				new HashMap<String, Object>());
	}

	/**
	 * 更新属性表数据。
	 * 
	 * @param cn
	 * @param dialect
	 * @param table
	 * @param model
	 * @param condition
	 * @param property
	 * @param propertyModelMapper
	 * @param originalPropertyValue
	 *            原始属性值
	 * @param updatePropertyValue
	 *            待更新的属性值，允许为{@code null}
	 * @return
	 */
	public int updatePropertyTableData(Connection cn, Dialect dialect, String table, Model model, SqlBuilder condition,
			Property property, PropertyModelMapper<?> propertyModelMapper, Object originalPropertyValue,
			Object updatePropertyValue)
	{
		return updatePropertyTableData(cn, dialect, table, model, condition, property, propertyModelMapper,
				originalPropertyValue, updatePropertyValue, null, true, new HashMap<String, Object>());
	}

	/**
	 * 更新。
	 * 
	 * @param cn
	 * @param dialect
	 * @param table
	 * @param model
	 * @param originalCondition
	 *            用于确定原始数据记录的模型表条件
	 * @param originalObj
	 *            原始数据
	 * @param updateObj
	 *            待更新的数据
	 * @param extraColumnNames
	 *            附加列名称数组，允许为{@code null}
	 * @param extraColumnValues
	 *            附加列值，允许为{@code null}
	 * @param ignorePropertyName
	 *            忽略的属性名称，用于处理双向关联时，允许为{@code null}
	 * @param sqlResultMap
	 *            用于缓存SQL表达式求值结果的映射表
	 * @return
	 */
	protected int update(Connection cn, Dialect dialect, String table, Model model, SqlBuilder originalCondition,
			Object originalObj, Object updateObj, String[] extraColumnNames, Object[] extraColumnValues,
			String ignorePropertyName, Map<String, Object> sqlResultMap)
	{
		int count = 0;

		Property[] properties = model.getProperties();
		RelationMapper[] relationMappers = getRelationMappers(model);

		Object[] originalPropertyValues = MU.getPropertyValues(model, originalObj);
		Object[] updatePropertyValues = MU.getPropertyValues(model, updateObj, properties);

		// 先求得SQL表达式属性值并赋予obj，因为某些驱动程序并不支持任意设置Statement.getGeneratedKeys()
		for (int i = 0; i < properties.length; i++)
		{
			Property property = properties[i];

			if (isUpdateIgnoreProperty(model, property, ignorePropertyName, true))
				continue;

			Object propertyValue = updatePropertyValues[i];

			if (PMU.isSqlExpression(propertyValue))
			{
				propertyValue = executeQueryForGetPropertySqlValueResult(cn, model, property, (String) propertyValue,
						sqlResultMap, this.conversionService);

				updatePropertyValues[i] = propertyValue;
				property.set(updateObj, propertyValue);
			}
		}

		List<UpdateInfoForAutoKeyUpdateRule> updateInfoForAutoKeyUpdateRules = new ArrayList<UpdateInfoForAutoKeyUpdateRule>();

		// 先处理删除属性值，它不会受外键约束的影响；
		// 先处理KeyRule.isManually()为true的更新属性值操作，它不会受外键约束的影响，并且如果先更新模型表，里更的外键值可能会被更新，那么关联属性值更新则会失效；
		for (int i = 0; i < properties.length; i++)
		{
			Property property = properties[i];

			if (isUpdateIgnoreProperty(model, property, ignorePropertyName, false))
				continue;

			if (MU.isMultipleProperty(property))
			{
				// TODO 处理集合属性值更新
			}

			Object originalPropertyValue = originalPropertyValues[i];
			Object updatePropertyValue = updatePropertyValues[i];
			RelationMapper relationMapper = relationMappers[i];

			if (updatePropertyValue == null)
			{
				if (originalPropertyValue != null)
					deletePersistenceOperation.deletePropertyTableData(cn, dialect, table, model, originalCondition,
							property, relationMapper, null, false);
				else
					;
			}
			else
			{
				PropertyModelMapper<?>[] propertyModelMappers = PropertyModelMapper.valueOf(property, relationMapper);

				int myMapperIndex = MU.getModelIndex(property.getModels(), updatePropertyValue);

				for (int j = 0; j < propertyModelMappers.length; j++)
				{
					PropertyModelMapper<?> pmm = propertyModelMappers[j];
					Model propertyModel = pmm.getModel();

					if (j == myMapperIndex)
					{
						if (PMU.isShared(model, property, propertyModel))
							continue;

						KeyRule propertyKeyUpdateRule = pmm.getMapper().getPropertyKeyUpdateRule();

						if (propertyKeyUpdateRule == null || propertyKeyUpdateRule.isManually())
						{
							int myUpdateCount = updatePropertyTableData(cn, dialect, table, model, originalCondition,
									property, pmm, originalPropertyValue, updatePropertyValue, updateObj, false,
									sqlResultMap);

							if (myUpdateCount == 0)
								insertPersistenceOperation.insertPropertyTableData(cn, dialect, table, model, updateObj,
										property, pmm, new Object[] { updatePropertyValue }, null, sqlResultMap);
						}
						else
						{
							UpdateInfoForAutoKeyUpdateRule updateInfo = new UpdateInfoForAutoKeyUpdateRule(property, i,
									pmm, j, updatePropertyValue);
							updateInfoForAutoKeyUpdateRules.add(updateInfo);
						}
					}
					else
					{
						deletePersistenceOperation.deletePropertyTableData(cn, dialect, table, model, originalCondition,
								property, pmm, null, false);
					}
				}
			}
		}

		// 更新模型表数据
		count = updateModelTableData(cn, dialect, table, model, originalCondition, properties, updateObj,
				originalPropertyValues, extraColumnNames, extraColumnValues, ignorePropertyName);

		// 处理KeyRule.isManually()为false的更新属性值操作
		if (!updateInfoForAutoKeyUpdateRules.isEmpty())
		{
			SqlBuilder updateCondition = buildRecordCondition(cn, dialect, model, updateObj, null);

			for (UpdateInfoForAutoKeyUpdateRule updateInfo : updateInfoForAutoKeyUpdateRules)
			{
				Object updatePropertyValue = updateInfo.getUpdatePropertyValue();

				int myUpdateCount = updatePropertyTableData(cn, dialect, table, model, updateCondition,
						updateInfo.getProperty(), updateInfo.getPropertyModelMapper(),
						originalPropertyValues[updateInfo.getPropertyIndex()], updatePropertyValue, null, false,
						sqlResultMap);

				if (myUpdateCount == 0)
					insertPersistenceOperation.insertPropertyTableData(cn, dialect, table, model, updateObj,
							updateInfo.getProperty(), updateInfo.getPropertyModelMapper(),
							new Object[] { updatePropertyValue }, null, sqlResultMap);
			}
		}

		return count;
	}

	/**
	 * 更新模型表数据。
	 * 
	 * @param cn
	 * @param dialect
	 * @param table
	 * @param model
	 * @param condition
	 *            模型表查询条件，允许为{@code null}
	 * @param updateProperties
	 * @param updateObj
	 * @param originalPropertyValues
	 *            允许为{@code null}或者元素为{@code null}
	 * @param extraColumnNames
	 *            附加列名称数组，允许为{@code null}
	 * @param extraColumnValues
	 *            附加列值，允许为{@code null}
	 * @param ignorePropertyName
	 *            忽略的属性名称，用于处理双向关联时，允许为{@code null}
	 * @return
	 */
	protected int updateModelTableData(Connection cn, Dialect dialect, String table, Model model, SqlBuilder condition,
			Property[] updateProperties, Object updateObj, Object[] originalPropertyValues, String[] extraColumnNames,
			Object[] extraColumnValues, String ignorePropertyName)
	{
		SqlBuilder sql = SqlBuilder.valueOf().sql("UPDATE ").sql(toQuoteName(dialect, table)).sql(" SET ").delimit(",");
		int sqlLength = sql.sqlLength();

		ModelOrderGenerator modelOrderGenerator = new ModelOrderGenerator()
		{
			@Override
			public long generate(Model model, Property property,
					PropertyModelMapper<ModelTableMapper> propertyModelMapper, Object propertyValue,
					Object[] propertyKeyColumnValues)
			{
				// TODO 实现排序值生成逻辑
				return 0;
			}
		};

		for (int i = 0; i < updateProperties.length; i++)
		{
			Property property = updateProperties[i];

			if (isUpdateIgnoreProperty(model, property, ignorePropertyName, true))
				continue;

			Object originalPropertyValue = (originalPropertyValues == null ? null : originalPropertyValues[i]);
			Object updatePropertyValue = MU.getPropertyValue(model, updateObj, property);

			// 如果属性值未修改，则不更新
			if (isPropertyValueUnchangedForUpdateModelTableData(model, property, originalPropertyValue,
					updatePropertyValue))
				continue;

			RelationMapper relationMapper = getRelationMapper(model, property);
			PropertyModelMapper<?>[] propertyModelMappers = PropertyModelMapper.valueOf(property, relationMapper);

			List<Object> myOriginalColumnValues = new ArrayList<Object>();
			List<Object> myUpdateColumnValues = new ArrayList<Object>();

			addColumnValues(cn, model, property, propertyModelMappers, originalPropertyValue, true, modelOrderGenerator,
					true, myOriginalColumnValues);
			addColumnValues(cn, model, property, propertyModelMappers, updatePropertyValue, true, modelOrderGenerator,
					true, myUpdateColumnValues);

			if (myOriginalColumnValues.equals(myUpdateColumnValues))
				continue;

			List<String> myColumnNames = new ArrayList<String>();
			addColumnNames(model, property, propertyModelMappers, true, true, true, myColumnNames);

			sql.sqldSuffix(toQuoteNames(dialect, toStringArray(myColumnNames)), "=?")
					.arg(toObjectArray(myUpdateColumnValues));
		}

		if (extraColumnNames != null)
			sql.sqldSuffix(toQuoteNames(dialect, extraColumnNames), "=?").arg(extraColumnValues);

		int nowSqlLength = sql.sqlLength();

		if (condition != null)
			sql.sql(" WHERE ").sql(condition);

		if (nowSqlLength == sqlLength)
			return UNCHANGED;
		else
			return executeUpdate(cn, sql);
	}

	/**
	 * 更新属性表数据。
	 * 
	 * @param cn
	 * @param dialect
	 * @param table
	 * @param model
	 * @param condition
	 * @param property
	 * @param propertyModelMapper
	 * @param originalPropertyValue
	 *            原始属性值
	 * @param updatePropertyValue
	 *            待更新的属性值，允许为{@code null}
	 * @param keyUpdateObj
	 *            需要处理外键更新的对象，允许为{@code null}
	 * @param updateModelTable
	 *            是否更新模型表数据
	 * @param sqlResultMap
	 *            用于缓存SQL表达式求值结果的映射表
	 * @return
	 */
	protected int updatePropertyTableData(Connection cn, Dialect dialect, String table, Model model,
			SqlBuilder condition, Property property, PropertyModelMapper<?> propertyModelMapper,
			Object originalPropertyValue, Object updatePropertyValue, Object keyUpdateObj, boolean updateModelTable,
			Map<String, Object> sqlResultMap)
	{
		int count = 0;

		if (propertyModelMapper.isModelTableMapperInfo())
		{
			PropertyModelMapper<ModelTableMapper> mpmm = propertyModelMapper.castModelTableMapperInfo();

			count = updatePropertyTableDataForModelTableMapper(cn, dialect, table, model, condition, property, mpmm,
					originalPropertyValue, updatePropertyValue, updateModelTable, sqlResultMap);
		}
		else if (propertyModelMapper.isPropertyTableMapperInfo())
		{
			PropertyModelMapper<PropertyTableMapper> ppmm = propertyModelMapper.castPropertyTableMapperInfo();

			count = updatePropertyTableDataForPropertyTableMapper(cn, dialect, table, model, condition, property, ppmm,
					originalPropertyValue, updatePropertyValue, keyUpdateObj, sqlResultMap);
		}
		else if (propertyModelMapper.isJoinTableMapperInfo())
		{
			PropertyModelMapper<JoinTableMapper> jpmm = propertyModelMapper.castJoinTableMapperInfo();

			count = updatePropertyTableDataForJoinTableMapper(cn, dialect, table, model, condition, property, jpmm,
					originalPropertyValue, updatePropertyValue, keyUpdateObj, sqlResultMap);
		}
		else
			throw new UnsupportedOperationException();

		return count;
	}

	/**
	 * 更新属性表数据。
	 * 
	 * @param cn
	 * @param dialect
	 * @param table
	 * @param model
	 * @param condition
	 *            模型表查询条件，允许为{@code null}
	 * @param property
	 * @param propertyModelMapper
	 * @param originalPropertyValue
	 *            原始属性值，基本属性值时允许为{@code null}
	 * @param updatePropertyValue
	 *            待更新的属性值，允许为{@code null}
	 * @param updateModelTable
	 *            是否更新模型表数据
	 * @param sqlResultMap
	 *            用于缓存SQL表达式求值结果的映射表
	 * @return
	 */
	protected int updatePropertyTableDataForModelTableMapper(Connection cn, Dialect dialect, String table, Model model,
			SqlBuilder condition, Property property, PropertyModelMapper<ModelTableMapper> propertyModelMapper,
			Object originalPropertyValue, Object updatePropertyValue, boolean updateModelTable,
			Map<String, Object> sqlResultMap)
	{
		int count = 0;

		ModelTableMapper mapper = propertyModelMapper.getMapper();

		if (mapper.isPrimitivePropertyMapper())
		{
			if (updateModelTable)
			{
				Property[] properties = new Property[] { property };
				Object[] originalPropertyValues = new Object[] { originalPropertyValue };

				Object updateObj = model.newInstance();
				property.set(updateObj, updatePropertyValue);

				count = updateModelTableData(cn, dialect, table, model, condition, properties, updateObj,
						originalPropertyValues, null, null, null);
			}
			else
				count = PERSISTENCE_IGNORED;
		}
		else
		{
			Model pmodel = propertyModelMapper.getModel();

			if (PMU.isPrivate(model, property, pmodel))
			{
				count = update(cn, dialect, table, pmodel,
						buildRecordCondition(cn, dialect, pmodel, originalPropertyValue, null), originalPropertyValue,
						updatePropertyValue, null, null, getMappedByWith(mapper), sqlResultMap);

				if (updateModelTable)
				{
					Property[] properties = new Property[] { property };
					Object[] originalPropertyValues = new Object[] { originalPropertyValue };

					Object updateObj = model.newInstance();
					property.set(updateObj, updatePropertyValue);

					count = updateModelTableData(cn, dialect, table, model, condition, properties, updateObj,
							originalPropertyValues, null, null, null);
				}
			}
			else
			{
				if (updateModelTable)
				{
					Property[] properties = new Property[] { property };
					Object[] originalPropertyValues = new Object[] { originalPropertyValue };

					Object updateObj = model.newInstance();
					property.set(updateObj, updatePropertyValue);

					count = updateModelTableData(cn, dialect, table, model, condition, properties, updateObj,
							originalPropertyValues, null, null, null);
				}
				else
					return PERSISTENCE_IGNORED;
			}
		}

		return count;
	}

	/**
	 * 更新属性表数据。
	 * 
	 * @param cn
	 * @param dialect
	 * @param table
	 * @param model
	 * @param condition
	 * @param property
	 * @param propertyModelMapper
	 *            属性表查询条件，允许为{@code null}。
	 * @param originalPropertyValue
	 *            原始属性值
	 * @param updatePropertyValue
	 *            待更新的属性值，允许为{@code null}
	 * @param keyUpdateObj
	 *            需要处理外键更新的对象，允许为{@code null}
	 * @param sqlResultMap
	 *            用于缓存SQL表达式求值结果的映射表
	 * @return
	 */
	protected int updatePropertyTableDataForPropertyTableMapper(Connection cn, Dialect dialect, String table,
			Model model, SqlBuilder condition, Property property,
			PropertyModelMapper<PropertyTableMapper> propertyModelMapper, Object originalPropertyValue,
			Object updatePropertyValue, Object keyUpdateObj, Map<String, Object> sqlResultMap)
	{
		int count = 0;

		PropertyTableMapper mapper = propertyModelMapper.getMapper();
		Model propertyModel = propertyModelMapper.getModel();

		String[] mkeyColumnNames = null;
		Object[] mkeyColumnValues = null;

		if (keyUpdateObj != null)
		{
			mkeyColumnNames = mapper.getModelKeyColumnNames();
			mkeyColumnValues = getModelKeyColumnValues(cn, mapper, model, keyUpdateObj);
		}

		// 如果是单元属性，则不必需要recordCondtion
		SqlBuilder recordCondition = (MU.isMultipleProperty(property)
				? buildRecordCondition(cn, dialect, propertyModel, originalPropertyValue, getMappedByWith(mapper))
				: null);

		SqlBuilder ptableCondition = buildPropertyTableConditionForPropertyTableMapper(dialect, table, model, condition,
				property, propertyModelMapper, recordCondition);

		if (mapper.isPrimitivePropertyMapper())
		{
			String ptable = mapper.getPrimitiveTableName();

			boolean changed = !isPropertyValueUnchangedForUpdateModelTableData(model, property, originalPropertyValue,
					updatePropertyValue);

			if (!changed && mkeyColumnNames == null)
				count = UNCHANGED;
			else
			{
				SqlBuilder sql = SqlBuilder.valueOf();

				sql.sql("UPDATE ").sql(toQuoteName(dialect, ptable)).sql(" SET ").delimit(",");

				if (changed)
				{
					String columnName = toQuoteName(dialect, mapper.getPrimitiveColumnName());

					if (PMU.isSqlExpression(updatePropertyValue))
					{
						String sqlValue = PMU.getSqlForSqlExpression(updatePropertyValue);
						sql.sqldSuffix(columnName, "=" + sqlValue);
					}
					else
					{
						Object columnValue = getColumnValue(cn, model, property, propertyModelMapper,
								updatePropertyValue);
						sql.sqldSuffix(columnName, "=?").arg(columnValue);
					}
				}

				if (mkeyColumnNames != null)
					sql.sqldSuffix(mkeyColumnNames, "=?").arg(mkeyColumnValues);

				sql.sql(" WHERE ").sql(ptableCondition);

				count = executeUpdate(cn, sql);
			}
		}
		else
		{
			count = update(cn, dialect, getTableName(propertyModel), propertyModel, ptableCondition,
					originalPropertyValue, updatePropertyValue, mkeyColumnNames, mkeyColumnValues,
					getMappedByWith(propertyModelMapper.getMapper()), sqlResultMap);
		}

		return count;
	}

	/**
	 * 更新属性表数据。
	 * 
	 * @param cn
	 * @param dialect
	 * @param table
	 * @param model
	 * @param condition
	 * @param property
	 * @param propertyModelMapper
	 * @param originalPropertyValue
	 *            原始属性值
	 * @param updatePropertyValue
	 *            待更新的属性值，允许为{@code null}
	 * @param keyUpdateObj
	 *            需要处理外键更新的对象，允许为{@code null}
	 * @param sqlResultMap
	 *            用于缓存SQL表达式求值结果的映射表
	 * @return
	 */
	protected int updatePropertyTableDataForJoinTableMapper(Connection cn, Dialect dialect, String table, Model model,
			SqlBuilder condition, Property property, PropertyModelMapper<JoinTableMapper> propertyModelMapper,
			Object originalPropertyValue, Object updatePropertyValue, Object keyUpdateObj,
			Map<String, Object> sqlResultMap)
	{
		int count = 0;

		Model propertyModel = propertyModelMapper.getModel();

		// 如果是单元属性，则不必需要recordCondtion
		SqlBuilder recordCondition = (MU.isMultipleProperty(property)
				? buildRecordCondition(cn, dialect, propertyModel, originalPropertyValue,
						getMappedByWith(propertyModelMapper.getMapper()))
				: null);

		SqlBuilder ptableCondition = buildPropertyTableConditionForJoinTableMapper(dialect, table, propertyModel,
				condition, property, propertyModelMapper, recordCondition);

		if (PMU.isPrivate(model, property, propertyModel))
		{
			count = update(cn, dialect, getTableName(propertyModel), propertyModel, ptableCondition,
					originalPropertyValue, updatePropertyValue, null, null,
					getMappedByWith(propertyModelMapper.getMapper()), sqlResultMap);

			if (keyUpdateObj != null)
			{
				count = updatePropertyTableDataRelationForJoinTableMapper(cn, dialect, table, propertyModel, condition,
						property, propertyModelMapper, originalPropertyValue, updatePropertyValue, keyUpdateObj);
			}
		}
		else
		{
			if (keyUpdateObj != null)
			{
				count = updatePropertyTableDataRelationForJoinTableMapper(cn, dialect, table, propertyModel, condition,
						property, propertyModelMapper, originalPropertyValue, updatePropertyValue, keyUpdateObj);
			}
			else
				count = PERSISTENCE_IGNORED;
		}

		return count;
	}

	/**
	 * 更新属性表数据的关联关系。
	 * 
	 * @param cn
	 * @param dialect
	 * @param table
	 * @param model
	 * @param condition
	 * @param property
	 * @param propertyModelMapper
	 * @param originalPropertyValue
	 * @param updatePropertyValue
	 * @param keyUpdateObj
	 * @return
	 */
	protected int updatePropertyTableDataRelationForJoinTableMapper(Connection cn, Dialect dialect, String table,
			Model model, SqlBuilder condition, Property property,
			PropertyModelMapper<JoinTableMapper> propertyModelMapper, Object originalPropertyValue,
			Object updatePropertyValue, Object keyUpdateObj)
	{
		JoinTableMapper mapper = propertyModelMapper.getMapper();

		SqlBuilder propertyTableCondition = buildRecordCondition(cn, dialect, propertyModelMapper.getModel(),
				originalPropertyValue, null);
		SqlBuilder joinTableCondtion = buildJoinTableCondition(dialect, table, model, condition, property,
				propertyModelMapper, propertyTableCondition);

		String joinTableName = toQuoteName(dialect, mapper.getJoinTableName());
		String[] mkeyColumnNames = toQuoteNames(dialect, mapper.getModelKeyColumnNames());
		String[] pkeyColumnNames = toQuoteNames(dialect, mapper.getPropertyKeyColumnNames());

		Object[] updateModelKeyColumnValues = getModelKeyColumnValues(cn, mapper, model, keyUpdateObj);
		Object[] updatePropertyKeyColumnValues = getPropertyKeyColumnValues(cn, mapper, propertyModelMapper.getModel(),
				updatePropertyValue);

		SqlBuilder sql = SqlBuilder.valueOf();

		sql.sql("UPDATE ").sql(joinTableName).sql(" SET ").delimit(",").sqldSuffix(mkeyColumnNames, "=?")
				.arg(updateModelKeyColumnValues).sqldSuffix(pkeyColumnNames, "=?").arg(updatePropertyKeyColumnValues)
				.sql(" WHERE ").sql(joinTableCondtion);

		return executeUpdate(cn, sql);
	}

	/**
	 * 是否是更新忽略属性。
	 * 
	 * @param model
	 * @param property
	 * @param ignorePropertyName
	 * @param forceIgnoreMultipleProperty
	 * @return
	 */
	protected boolean isUpdateIgnoreProperty(Model model, Property property, String ignorePropertyName,
			boolean forceIgnoreMultipleProperty)
	{
		if (ignorePropertyName != null && ignorePropertyName.equals(property.getName()))
			return true;

		if (property.hasFeature(NotReadable.class) || property.hasFeature(NotEditable.class))
			return true;

		if (MU.isMultipleProperty(property))
		{
			if (forceIgnoreMultipleProperty)
				return true;

			return (!this.handleMultipleProperty);
		}

		return false;
	}

	/**
	 * 判断属性值是否未作修改。
	 * 
	 * @param model
	 * @param property
	 * @param originalPropertyValue
	 * @param updatePropertyValue
	 * @return
	 */
	protected boolean isPropertyValueUnchangedForUpdateModelTableData(Model model, Property property,
			Object originalPropertyValue, Object updatePropertyValue)
	{
		if (MU.isMultipleProperty(property))
			throw new UnsupportedOperationException();

		if (originalPropertyValue == null)
		{
			return (updatePropertyValue == null);
		}
		else if (updatePropertyValue == null)
		{
			return (originalPropertyValue == null);
		}
		else
		{
			PropertyModel originalPropertyModel = PropertyModel.valueOf(property, originalPropertyValue);
			PropertyModel updatePropertyModel = PropertyModel.valueOf(property, updatePropertyValue);

			if (originalPropertyModel.getIndex() != updatePropertyModel.getIndex())
				return false;

			Model pmodel = originalPropertyModel.getModel();

			// 仅比较基本属性值，复合属性值如果存在循环引用，equals会出现死循环
			if (MU.isPrimitiveModel(pmodel))
			{
				return (originalPropertyValue.equals(updatePropertyValue));
			}
			else
				return false;
		}
	}

	protected static class UpdateInfoForAutoKeyUpdateRule
	{
		private Property property;

		private int propertyIndex;

		private PropertyModelMapper<?> propertyModelMapper;

		private int propertyModelMapperIndex;

		private Object updatePropertyValue;

		public UpdateInfoForAutoKeyUpdateRule()
		{
			super();
		}

		public UpdateInfoForAutoKeyUpdateRule(Property property, int propertyIndex,
				PropertyModelMapper<?> propertyModelMapper, int propertyModelMapperIndex, Object updatePropertyValue)
		{
			super();
			this.property = property;
			this.propertyIndex = propertyIndex;
			this.propertyModelMapper = propertyModelMapper;
			this.propertyModelMapperIndex = propertyModelMapperIndex;
			this.updatePropertyValue = updatePropertyValue;
		}

		public Property getProperty()
		{
			return property;
		}

		public void setProperty(Property property)
		{
			this.property = property;
		}

		public int getPropertyIndex()
		{
			return propertyIndex;
		}

		public void setPropertyIndex(int propertyIndex)
		{
			this.propertyIndex = propertyIndex;
		}

		public PropertyModelMapper<?> getPropertyModelMapper()
		{
			return propertyModelMapper;
		}

		public void setPropertyModelMapper(PropertyModelMapper<?> propertyModelMapper)
		{
			this.propertyModelMapper = propertyModelMapper;
		}

		public int getPropertyModelMapperIndex()
		{
			return propertyModelMapperIndex;
		}

		public void setPropertyModelMapperIndex(int propertyModelMapperIndex)
		{
			this.propertyModelMapperIndex = propertyModelMapperIndex;
		}

		public Object getUpdatePropertyValue()
		{
			return updatePropertyValue;
		}

		public void setUpdatePropertyValue(Object updatePropertyValue)
		{
			this.updatePropertyValue = updatePropertyValue;
		}
	}
}