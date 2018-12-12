<%--
/*
 * Copyright 2018 datagear.tech. All Rights Reserved.
 */
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="../include/jsp_import.jsp" %>
<%@ include file="../include/jsp_ajax_request.jsp" %>
<%@ include file="../include/jsp_jstl.jsp" %>
<%@ include file="../include/jsp_page_id.jsp" %>
<%@ include file="../include/jsp_method_get_string_value.jsp" %>
<%@ include file="../include/jsp_method_write_json.jsp" %>
<%@ include file="include/data_jsp_define.jsp" %>
<%@ include file="../include/html_doctype.jsp" %>
<%
//初始数据，允许null
Object data = request.getAttribute("data");
//标题操作标签I18N关键字，不允许null
String titleOperationMessageKey = getStringValue(request, "titleOperationMessageKey");
//提交活动，po.pageParam().submit(...)未定义时，不允许为null
String submitAction = getStringValue(request, "submitAction");
//是否是客户端操作，允许为null
boolean clientOperation = ("true".equalsIgnoreCase(getStringValue(request, "clientOperation")));
//是否只读操作，允许为null
boolean readonly = ("true".equalsIgnoreCase(getStringValue(request, "readonly")));
//忽略表单渲染和处理的属性名，允许为null
String ignorePropertyName = getStringValue(request, "ignorePropertyName", "");
//是否开启批量执行功能，允许为null
boolean batchSet = ("true".equalsIgnoreCase(getStringValue(request, "batchSet")));
%>
<html>
<head>
<%@ include file="../include/html_head.jsp" %>
<title>
	<%@ include file="../include/html_title_app_name.jsp" %>
	<fmt:message key='<%=titleOperationMessageKey%>' />
	<fmt:message key='titleSeparator' />
	<%=WebUtils.escapeHtml(ModelUtils.displayName(model, WebUtils.getLocale(request)))%>
</title>
</head>
<body>
<div id="${pageId}" class="page-form">
	<div class="head">
	</div>
	<div class="content">
		<form id="${pageId}-form" method="POST">
		</form>
	</div>
	<div class="foot">
	</div>
</div>
<%@ include file="include/data_page_obj.jsp" %>
<%@ include file="include/data_page_obj_form.jsp" %>
<script type="text/javascript">
(function(po)
{
	po.readonly = <%=readonly%>;
	po.submitAction = "<%=submitAction%>";
	po.originalData = $.unref(<%writeJson(application, out, data);%>);
	po.data = $.unref($.ref(po.originalData));
	po.clientOperation = <%=clientOperation%>;
	po.batchSet = <%=batchSet%>;
	
	po.superBuildPropertyActionOptions = po.buildPropertyActionOptions;
	po.buildPropertyActionOptions = function(property, propertyConcreteModel, extraRequestParams, extraPageParams)
	{
		var actionParam = po.superBuildPropertyActionOptions(property, propertyConcreteModel, extraRequestParams, extraPageParams);
		
		//客户端操作则传递最新表单数据，因为不需要到服务端数据库查找验证
		if(po.clientOperation)
			actionParam["data"]["data"] = po.form().modelform("data");
		
		return actionParam;
	};
	
	po.onModel(function(model)
	{
		po.form().modelform(
		{
			model : model,
			ignorePropertyNames : "<%=WebUtils.escapeJavaScriptStringValue(ignorePropertyName)%>",
			data : po.data,
			readonly : po.readonly,
			submit : function()
			{
				var data = $(this).modelform("data");
				var formParam = $(this).modelform("param");
				
				var pageParam = po.pageParam();
				
				var close = true;
				
				//父页面定义了submit回调函数，则优先执行
				if(pageParam && pageParam.submit)
				{
					close = (pageParam.submit(data, formParam) != false);
					
					if(close && !$(this).modelform("isDialogPinned"))
						po.close();
				}
				//否则，POST至后台
				else
				{
					var thisForm = this;
					var param = $.extend(formParam, {"data" : data, "originalData" : po.originalData});
					
					po.ajaxSubmitForHandleDuplication(po.submitAction, param, "<fmt:message key='save.continueIgnoreDuplicationTemplate' />",
					{
						beforeSend : function()
						{
							$(thisForm).modelform("disableOperation");
						},
						success : function(operationMessage)
						{
							var $form = $(thisForm);
							var batchSubmit = $form.modelform("isBatchSubmit");
							var isDialogPinned = $form.modelform("isDialogPinned");
							
							$form.modelform("enableOperation");
							
							po.refreshParent();
							
							if(batchSubmit)
								;
							else
							{
								po.data = $.unref(operationMessage.data);
								//如果有初始数据，则更新为已保存至后台的数据
								//注意：不能直接赋值po.data，因为是同一个引用，有可能会被修改，而po.originalData不应该被修改
								if(po.originalData)
									po.originalData = $.unref($.ref(operationMessage.data));
								
								if(pageParam && pageParam.afterSave)
									close = (pageParam.afterSave(operationMessage.data) != false);
								
								if(close && !isDialogPinned)
									po.close();
							}
						},
						error : function()
						{
							var $form = $(thisForm);
							var batchSubmit = $form.modelform("isBatchSubmit");
							
							$form.modelform("enableOperation");
							
							if(batchSubmit)
								po.refreshParent();
						}
					});
				}
				
				return false;
			},
			addSinglePropertyValue : function(property, propertyConcreteModel)
			{
				po.addSinglePropertyValue(property, propertyConcreteModel);
			},
			editSinglePropertyValue : function(property, propertyConcreteModel)
			{
				po.editSinglePropertyValue(property, propertyConcreteModel);
			},
			deleteSinglePropertyValue : function(property, propertyConcreteModel)
			{
				po.deleteSinglePropertyValue(property, propertyConcreteModel);
			},
			selectSinglePropertyValue : function(property, propertyConcreteModel)
			{
				po.selectSinglePropertyValue(property, propertyConcreteModel);
			},
			viewSinglePropertyValue : function(property, propertyConcreteModel)
			{
				po.viewSinglePropertyValue(property, propertyConcreteModel);
			},
			editMultiplePropertyValue : function(property, propertyConcreteModel)
			{
				po.editMultiplePropertyValue(property, propertyConcreteModel);
			},
			viewMultiplePropertyValue : function(property, propertyConcreteModel)
			{
				po.viewMultiplePropertyValue(property, propertyConcreteModel);
			},
			filePropertyUploadURL : "<c:url value='/data/file/upload' />",
			filePropertyDeleteURL : "<c:url value='/data/file/delete' />",
			downloadSinglePropertyValueFile : function(property, propertyConcreteModel)
			{
				po.downloadSinglePropertyValueFile(property, propertyConcreteModel);
			},
			validationRequiredAsAdd : ("saveAdd" == po.submitAction),
			batchSet : po.batchSet,
			labels : po.formLabels,
			dateFormat : "<c:out value='${sqlDateFormat}' />",
			timestampFormat : "<c:out value='${sqlTimestampFormat}' />",
			timeFormat : "<c:out value='${sqlTimeFormat}' />"
		});
	});
})
(${pageId});
</script>
</body>
</html>
