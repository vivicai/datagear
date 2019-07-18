<#--
导入公用片段

String dataExchangeId 数据交换ID
String dataExchangeChannelId 数据交换cometd通道ID

依赖：
dataExchange_js.ftl

-->
<script type="text/javascript">
(function(po)
{
	po.dataExchangeChannelId = "${dataExchangeChannelId}";
	
	po.addSubDataExchangesForFileInfos = function(fileInfos)
	{
		if(!fileInfos.length)
			return;
		
		for(var i=0; i<fileInfos.length; i++)
		{
			fileInfos[i].subDataExchangeId = po.nextSubDataExchangeId();
			fileInfos[i].number = po.currentSubDataExchangeId();
			fileInfos[i].dependentNumber = "<@spring.message code='dataImport.none' />";
			
			po.postBuildSubDataExchange(fileInfos[i]);
		}
		
		po.addRowData(fileInfos);
	};
	
	po.postBuildSubDataExchange = function(subDataExchange){};
	
	po.fileUploadInfo = function(){ return this.element(".file-info"); };
	
	po.renderFileNameColumn = function(fileName)
	{
		if(!fileName)
			return "";
		
		if(fileName.length <= 10 + 3 + 20)
			return fileName;
		
		return "<span title='"+$.escapeHtml(fileName)+"'>" + fileName.substr(0, 10) + "..." + fileName.substr(fileName.length - 20) +"</span>";
	};
	
	po.dataImportTableColumns =
	[
		{
			title : "<@spring.message code='dataImport.number' />",
			data : "number",
			render : function(data, type, row, meta)
			{
				return $.escapeHtml(data) + "<input type='hidden' name='numbers' value='"+$.escapeHtml(data)+"' class='table-number-input ui-widget ui-widget-content' style='width:90%' />";
			},
			defaultContent: "",
			width : "10%"
		},
		{
			title : "<@spring.message code='dataImport.importFileName' />",
			data : "displayName",
			render : function(data, type, row, meta)
			{
				return po.renderFileNameColumn(data)
					+ "<input type='hidden' name='subDataExchangeIds' value='"+$.escapeHtml(row.subDataExchangeId)+"' />"
					+ "<input type='hidden' name='fileNames' value='"+$.escapeHtml(row.name)+"' />";
			},
			defaultContent: "",
			width : "45%",
		},
		{
			title : "<@spring.message code='dataImport.importFileSize' />",
			data : "size",
			render : po.renderColumn,
			defaultContent: "",
			width : "10%"
		},
		{
			title : "<@spring.message code='dataImport.dependentNumber' />",
			data : "dependentNumber",
			render : function(data, type, row, meta)
			{
				return "<input type='text' name='dependentNumbers' value='"+$.escapeHtml(data)+"' class='table-number-input ui-widget ui-widget-content' style='width:90%' />";
			},
			defaultContent: "",
			width : "10%"
		},
		{
			title : $.buildDataTablesColumnTitleWithTip("<@spring.message code='dataImport.importProgress' />", "<@spring.message code='dataImport.importStatusWithSuccessFail' />"),
			data : "status",
			render : function(data, type, row, meta)
			{
				if(!data)
					return "<@spring.message code='dataExchange.exchangeStatus.Unstart' />";
				else
					return data;
			},
			defaultContent: "",
			width : "25%"
		}
	];
	
	po.initDataImportSteps = function()
	{
		po.element(".form-content").steps(
		{
			headerTag: "h3",
			bodyTag: "div",
			onStepChanged : function(event, currentIndex, priorIndex)
			{
				if(currentIndex == 1)
					po.adjustDataTable();
			},
			onFinished : function(event, currentIndex)
			{
				po.element("#${pageId}-form").submit();
			},
			labels:
			{
				previous: "<@spring.message code='wizard.previous' />",
				next: "<@spring.message code='wizard.next' />",
				finish: "<@spring.message code='import' />"
			}
		});
		
		po.element("#${pageId}-form .wizard .actions ul li:eq(2)").addClass("page-status-aware-enable edit-status-enable");
	};
	
	po.initDataImportUIs = function()
	{
		$.initButtons(po.element());
		po.element("#${pageId}-exceptionResolve").buttonset();
		po.element("select[name='fileEncoding']").selectmenu({ appendTo : po.element(), classes : { "ui-selectmenu-menu" : "file-encoding-selectmenu-menu" } });
		
		po.element("#${pageId}-exceptionResolve-0").click();
	};
	
	po.initDataImportDataTable = function()
	{
		po.expectedResizeDataTableElements = [po.table()[0]];
		
		var tableSettings = po.buildDataTableSettingsLocal(po.dataImportTableColumns, [], {"order": []});
		
		po.subDataExchangeStatusColumnIndex = tableSettings.columns.length - 1;
		
		po.initDataTable(tableSettings);
		po.bindResizeDataTable();
	};
	
	po.initDataImportActions = function()
	{
		var $fileinputButton = po.element(".fileinput-button");
		var uploadAction = $fileinputButton.attr("upload-action");
		
		po.element(".fileinput-button").fileupload(
		{
			url : "${contextPath}/dataexchange/" + po.schemaId +"/import/" + uploadAction,
			paramName : "file",
			success : function(serverFileInfos, textStatus, jqXHR)
			{
				$.fileuploadsuccessHandlerForUploadInfo(po.fileUploadInfo(), true);
				
				po.addSubDataExchangesForFileInfos(serverFileInfos);
			}
		})
		.bind('fileuploadadd', function (e, data)
		{
			$.fileuploadaddHandlerForUploadInfo(e, data, po.fileUploadInfo());
		})
		.bind('fileuploadprogressall', function (e, data)
		{
			$.fileuploadprogressallHandlerForUploadInfo(e, data, po.fileUploadInfo());
		});
		
		po.element(".table-delete-item-button").click(function()
		{
			po.executeOnSelects(function(rowDatas, rowIndexes)
			{
				po.deleteRow(rowIndexes);
			});
		});
		
		po.element(".table-cancel-import-button").click(function()
		{
			po.cancelSelectedSubDataExchange();
		});
		
		po.element(".restart-button").click(function()
		{
			po.updateDataExchangePageStatus("edit");
		});
		
		po.table().on("click", ".exchange-result-icon", function(event)
		{
			//阻止行选中
			event.stopPropagation();
			
			var $this = $(this);
			
			if($this.hasClass("exchange-error-icon"))
			{
				var subDataExchangeId = $this.attr("subDataExchangeId");
				po.viewSubDataExchangeDetailLog(subDataExchangeId);
			}
		});
		
		po.element("#${pageId}-form").submit(function()
		{
			po.resetAllSubDataExchangeStatus();
			
			po.cometdExecuteAfterSubscribe(po.dataExchangeChannelId,
			function()
			{
				po.element("#${pageId}-form").ajaxSubmit(
				{
					success: function()
					{
						if(!po.isDataExchangePageStatus("finish"))
							po.updateDataExchangePageStatus("exchange");
					}
				});
			},
			function(message)
			{
				po.handleDataExchangeCometdMessage(message);
			});
			
			return false;
		});
	};
})
(${pageId});
</script>
</body>
</html>