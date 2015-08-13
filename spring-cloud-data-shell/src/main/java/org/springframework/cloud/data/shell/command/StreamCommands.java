/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.data.shell.command;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.data.rest.client.StreamOperations;
import org.springframework.cloud.data.rest.resource.StreamDefinitionResource;
import org.springframework.cloud.data.rest.util.DeploymentPropertiesUtils;
import org.springframework.cloud.data.shell.config.CloudDataShell;
import org.springframework.hateoas.PagedResources;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.support.table.Table;
import org.springframework.shell.support.table.TableHeader;
import org.springframework.stereotype.Component;

/**
 * Stream commands.
 *
 * @author Ilayaperumal Gopinathan
 * @author Mark Fisher
 */
@Component
// todo: reenable optionContext attributes
public class StreamCommands implements CommandMarker {

	private static final String LIST_STREAM = "stream list";

	private static final String CREATE_STREAM = "stream create";

	private static final String DEPLOY_STREAM = "stream deploy";

	private static final String UNDEPLOY_STREAM = "stream undeploy";

	private static final String UNDEPLOY_STREAM_ALL = "stream all undeploy";

	private static final String DESTROY_STREAM = "stream destroy";

	private static final String DESTROY_STREAM_ALL = "stream all destroy";

	private static final String PROPERTIES_OPTION = "properties";

	private static final String PROPERTIES_FILE_OPTION = "propertiesFile";

	@Autowired
	private CloudDataShell cloudDataShell;

	@Autowired
	private UserInput userInput;

	@CliAvailabilityIndicator({ LIST_STREAM, CREATE_STREAM, DEPLOY_STREAM, UNDEPLOY_STREAM, UNDEPLOY_STREAM_ALL,
		DESTROY_STREAM, DESTROY_STREAM_ALL })
	public boolean available() {
		return cloudDataShell.getCloudDataOperations() != null;
	}

	@CliCommand(value = LIST_STREAM, help = "List created streams")
	public Table listStreams() {
		final PagedResources<StreamDefinitionResource> streams = streamOperations().list();
		final Table table = new Table()
				.addHeader(1, new TableHeader("Stream Name"))
				.addHeader(2, new TableHeader("Stream Definition"))
				.addHeader(3, new TableHeader("Status"));
		for (StreamDefinitionResource stream : streams) {
			table.newRow()
					.addValue(1, stream.getName())
					.addValue(2, stream.getDslText())
					.addValue(3, stream.getStatus());
		}
		return table;
	}

	@CliCommand(value = CREATE_STREAM, help = "Create a new stream definition")
	public String createStream(
			@CliOption(mandatory = true, key = { "", "name" }, help = "the name to give to the stream") String name,
			@CliOption(mandatory = true, key = { "definition" }, help = "a stream definition, using the DSL (e.g. \"http --port=9000 | hdfs\")") String dsl,
			@CliOption(key = "deploy", help = "whether to deploy the stream immediately", unspecifiedDefaultValue = "false", specifiedDefaultValue = "true") boolean deploy) {
		streamOperations().createStream(name, dsl, deploy);
		return (deploy) ? String.format("Created and deployed new stream '%s'", name) : String.format(
				"Created new stream '%s'", name);
	}

	@CliCommand(value = DEPLOY_STREAM, help = "Deploy a previously created stream")
	public String deployStream(
			@CliOption(key = { "", "name" }, help = "the name of the stream to deploy", mandatory = true/*, optionContext = "existing-stream undeployed disable-string-converter"*/) String name,
			@CliOption(key = { PROPERTIES_OPTION }, help = "the properties for this deployment", mandatory = false) String properties,
			@CliOption(key = { PROPERTIES_FILE_OPTION }, help = "the properties for this deployment (as a File)", mandatory = false) File propertiesFile
			) throws IOException {
		int which = Assertions.atMostOneOf(PROPERTIES_OPTION, properties, PROPERTIES_FILE_OPTION, propertiesFile);
		Map<String, String> propertiesToUse;
		switch (which) {
			case 0:
				propertiesToUse = DeploymentPropertiesUtils.parse(properties);
				break;
			case 1:
				Properties props = new Properties();
				try (FileInputStream fis = new FileInputStream(propertiesFile)) {
					props.load(fis);
				}
				propertiesToUse = DeploymentPropertiesUtils.convert(props);
				break;
			case -1: // Neither option specified
				propertiesToUse = Collections.<String, String> emptyMap();
				break;
			default:
				throw new AssertionError();
		}
		streamOperations().deploy(name, propertiesToUse);
		return String.format("Deployed stream '%s'", name);
	}

	@CliCommand(value = UNDEPLOY_STREAM, help = "Un-deploy a previously deployed stream")
	public String undeployStream(
			@CliOption(key = { "", "name" }, help = "the name of the stream to un-deploy", mandatory = true/*, optionContext = "existing-stream deployed disable-string-converter"*/) String name
			) {
		streamOperations().undeploy(name);
		return String.format("Un-deployed stream '%s'", name);
	}

	@CliCommand(value = UNDEPLOY_STREAM_ALL, help = "Un-deploy all previously deployed stream")
	public String undeployAllStreams(
			@CliOption(key = "force", help = "bypass confirmation prompt", unspecifiedDefaultValue = "false", specifiedDefaultValue = "true") boolean force
			) {
		if (force || "y".equalsIgnoreCase(userInput.promptWithOptions("Really undeploy all streams?", "n", "y", "n"))) {
			streamOperations().undeployAll();
			return String.format("Un-deployed all the streams");
		}
		else {
			return "";
		}
	}

	@CliCommand(value = DESTROY_STREAM, help = "Destroy an existing stream")
	public String destroyStream(
			@CliOption(key = { "", "name" }, help = "the name of the stream to destroy", mandatory = true/*, optionContext = "existing-stream disable-string-converter"*/) String name) {
		streamOperations().destroy(name);
		return String.format("Destroyed stream '%s'", name);
	}

	@CliCommand(value = DESTROY_STREAM_ALL, help = "Destroy all existing streams")
	public String destroyAllStreams(
			@CliOption(key = "force", help = "bypass confirmation prompt", unspecifiedDefaultValue = "false", specifiedDefaultValue = "true") boolean force) {
		if (force || "y".equalsIgnoreCase(userInput.promptWithOptions("Really destroy all streams?", "n", "y", "n"))) {
			streamOperations().destroyAll();
			return "Destroyed all streams";
		}
		else {
			return "";
		}
	}

	StreamOperations streamOperations() {
		return cloudDataShell.getCloudDataOperations().streamOperations();
	}
}
