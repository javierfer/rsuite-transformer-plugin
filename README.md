# rsuite-transformer-plugin

Transforms XML managed objects using a given XSLT file. The process also updates/inserts the output into the same container.

## Context menu configuration sample

```
<extensionProvider id="rsuite.ContextMenu">
	<contextMenuRuleSet name="ditaOutputActionsID">
		<menuItemList>
			<menuItem id="rsuite:CCSSI2DITA2">
				<actionName>rsuite:invokeWebservice</actionName>
				<label>Transform CCSSI to DITA</label>
				<property name="rsuite:icon" value="ditaTopic" />
				<property name="remoteApiName" value="@pluginId@:webservice.transfomer" />
				<property name="showProgressMeter" value="true" />
				<property name="timeout" value="0" />
				<property name="serviceParams.transform" value="rsuite:/res/plugin/rsuite_eled/xslt/ccsi2dita/CCSSI2DITA.xsl" />
				<property name="serviceParams.output-filename-alias" value="ela-literacy-dita.dita" />
				<property name="serviceParams.output-file-extension-alias" value="dita" />
				<property name="serviceParams.host-protocol" value="https" />
				<property name="serviceParams.operation" value="update" />
				<property name="rsuite:group" value="rsuite:hierarchy" />
				<property name="rsuite:icon" value="generateXml" />
			</menuItem>
		</menuItemList>
		<ruleList>
			<rule>include elementType LearningStandards</rule>
			<rule>exclude role Contributor</rule>
		</ruleList>
	</contextMenuRuleSet>
</extensionProvider>
```

## Parameters
* serviceParams.transform: (required) Path in the plugin to the transform.
* serviceParams.output-filename-alias: (optional) File name alias for the generated output. If not provided, the file name will be the same as the source.
* serviceParams.output-file-extension-alias: (optional) File extension for the generated output. If not provided, the file name extension will be the same as the source.
* serviceParams.host-protocol: (optional) http/https (default "http"). Used to build the url to pass the "rsuite.serverurl" XSLT parameter to the transform. Additionally, "rsuite.sessionkey" and "rsuite.username" are passed.
* serviceParams.operation: (optional) See "Operations supported" (default "update").

## Operations supported

Use the parameter "serviceParams.operation"to specify an operation strategy:
* insert: Always inserts a new output in the container.
* update: Either inserts or updates the output in the container. This option does not overwrite the MO where the action is executed. To determine whether the output exists or not, the process iterates over all the MOs in the container and uses the file name aliases for comparison.
* update-overwrite: Always updates and overwrites the MO where the action is executed.

## Passing dynamic XSLT parameters

It is not possible to pass dynamic XSLT parameters from a context menu. Therefore, create a custom web service, change the "remoteApiName" to point to it, and include code like this to invoke the transformer plugin:

```
Map<String, String> params = new HashMap<String, String>();
params.put("XSLT_PARAM_myParam1", "myValue1");
params.put("XSLT_PARAM_myParam2", "myValue2");

RemoteApiResult remoteApiResult = context.getPluginAccessManager().execute(
		user, 
		SessionUtils.createSession(context, "my-project"), 
		"rsuite-transformer-plugin:webservice.transfomer", 
		MethodType.GET,
		CallArgumentList.fromMap(context, params));

if (remoteApiResult.getResponseStatus() != ResponseStatus.SUCCESS) {
	String apiErrorMsg = "Unexpected error invoking API: '" + apiToCall + 
			"'. Response Status: " + remoteApiResult.getResponseStatus();
	log.error(apiErrorMsg);
	throw new RSuiteException(RSuiteException.ERROR_INTERNAL_ERROR, 
			apiErrorMsg);
}
```

Note that each XSLT parameter key must start with "XSLT_PARAM_".

## Potential improvements
* Ability to transform all the MOs from a given content assembly.