package org.moskito.control.connectors;

import net.anotheria.util.StringUtils;
import org.distributeme.core.ServiceDescriptor;
import org.moskito.control.connectors.response.ConnectorAccumulatorResponse;
import org.moskito.control.connectors.response.ConnectorAccumulatorsNamesResponse;
import org.moskito.control.connectors.response.ConnectorStatusResponse;
import org.moskito.control.connectors.response.ConnectorThresholdsResponse;
import org.moskito.control.core.HealthColor;
import org.moskito.control.core.accumulator.AccumulatorDataItem;
import org.moskito.control.core.status.Status;
import org.moskito.controlagent.data.accumulator.AccumulatorHolder;
import org.moskito.controlagent.data.accumulator.AccumulatorListItem;
import org.moskito.controlagent.data.info.SystemInfo;
import org.moskito.controlagent.data.producers.ProducerInfo;
import org.moskito.controlagent.data.status.ThresholdInfo;
import org.moskito.controlagent.data.status.ThresholdStatusHolder;
import org.moskito.controlagent.data.threshold.ThresholdDataItem;
import org.moskito.controlagent.endpoints.rmi.AgentService;
import org.moskito.controlagent.endpoints.rmi.AgentServiceException;
import org.moskito.controlagent.endpoints.rmi.generated.AgentServiceConstants;
import org.moskito.controlagent.endpoints.rmi.generated.RemoteAgentServiceStub;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * TODO comment this class
 *
 * @author lrosenberg
 * @since 10.04.14 08:49
 */
public class RMIConnector extends AbstractConnector {

	private AgentService theOtherSideEndpoint;
	private String location;

	@Override
	public void configure(String location, String credentials) {
		this.location = location;
		String tokens[] = StringUtils.tokenize(location, ':');
		if (tokens.length!=2)
			throw new IllegalArgumentException("Location should be formed as host:port, misconfiguration in location: "+location);
		String host = tokens[0];
		int port = Integer.parseInt(tokens[1]);
		ServiceDescriptor descriptor = new ServiceDescriptor(ServiceDescriptor.Protocol.RMI, AgentServiceConstants.getServiceId(), "any", host, port);
		theOtherSideEndpoint = new RemoteAgentServiceStub(descriptor);
	}

	@Override
	public ConnectorStatusResponse getNewStatus() {
		HealthColor color = null;
		Status status = new Status();
		try{
			ThresholdStatusHolder holder = theOtherSideEndpoint.getThresholdStatus();
			color = HealthColor.getHealthColor(holder.getStatus());
			for (ThresholdInfo info : holder.getThresholds())
				status.addMessage(info.toString());
		}catch(AgentServiceException e){
			color = HealthColor.PURPLE;
			status.addMessage("Can't connect due to "+e.getMessage());
		}
		status.setHealth(color);
		ConnectorStatusResponse statusResponse = new ConnectorStatusResponse(status);
		return statusResponse;
	}

	@Override
	public ConnectorThresholdsResponse getThresholds() {
		try{
			List<ThresholdDataItem> thresholds = theOtherSideEndpoint.getThresholds();
			LinkedList<org.moskito.control.core.threshold.ThresholdDataItem> items = new LinkedList<org.moskito.control.core.threshold.ThresholdDataItem>();
			for (ThresholdDataItem t : thresholds){
				items.add(agent2control(t));
			}
			ConnectorThresholdsResponse response = new ConnectorThresholdsResponse(items);
			return response;
		}catch(AgentServiceException e){
			throw new ConnectorException("Couldn't obtain thresholds from server at "+location);
		}
	}

	@Override
	public ConnectorAccumulatorResponse getAccumulators(List<String> accumulatorNames) {
		ConnectorAccumulatorResponse response = new ConnectorAccumulatorResponse();
		try {
			Map<String, AccumulatorHolder> accumulators = theOtherSideEndpoint.getAccumulatorsData(accumulatorNames);
			for (String name : accumulatorNames){
				AccumulatorHolder holder = accumulators.get(name);
				LinkedList<AccumulatorDataItem> dataLine = new LinkedList<AccumulatorDataItem>();
				for (org.moskito.controlagent.data.accumulator.AccumulatorDataItem agentItem : holder.getItems()){
					dataLine.add(agent2control(agentItem));
				}
				response.addDataLine(name, dataLine);

			}
		}catch(AgentServiceException e){
			throw new ConnectorException("Can't retrieve accumulators from server at "+location,e );
		}
		return response;
	}

	@Override
	public ConnectorAccumulatorsNamesResponse getAccumulatorsNames(){
		try {
			List<AccumulatorListItem> accumulators = theOtherSideEndpoint.getAvailableAccumulators();
			LinkedList<String> names = new LinkedList<String>();
			for (AccumulatorListItem item : accumulators){
				names.add(item.getName());
			}
			return new ConnectorAccumulatorsNamesResponse(names);
		}catch(AgentServiceException e){
			throw new ConnectorException("Couldn't obtain accumulator names from server at "+location);
		}

	}

	/**
	 * Returns information about monitored app
	 * and its environment.
	 *
	 * Map has following fields:
	 * 	javaVersion  - version of java, where monitored app is launched
	 * 	startCommand - command, that launched monitored app
	 * 	machineName  - name of machine, where monitored app is launched.
	 * 	uptime       - monitored app uptime
	 *
	 * @return map with monitored app information
	 */
	@Override
	public Map<String, String> getInfo() {

		SystemInfo info;
		Map<String, String> infoMap = new HashMap<>();

		try {
			info = theOtherSideEndpoint.getSystemInfo();
		} catch (AgentServiceException e) {
			throw new ConnectorException("Couldn't obtain info from server at "+location);
		}

		if (info == null) {
			return infoMap;
		}

		infoMap.put("JVM Version", info.getJavaVersion());
		infoMap.put("Start Command", info.getStartCommand());
		infoMap.put("Machine Name", info.getMachineName());
		infoMap.put("Uptime", ""+info.getUptime());
		infoMap.put("Uphours", ""+info.getUphours());
		infoMap.put("Updays", ""+info.getUpdays());

		return infoMap;

	}

	public List<ProducerInfo> getProducers(){
		try {
			return theOtherSideEndpoint.getStatsInfo();
		} catch (AgentServiceException e) {
			throw new ConnectorException("Couldn't obtain info from server at "+location);
		}
	}

	/**
	 * Maps agent threshold item to internally used control item.
	 * @param agentItem
	 * @return threshold item
	 */
	private org.moskito.control.core.threshold.ThresholdDataItem agent2control(ThresholdDataItem agentItem){
		org.moskito.control.core.threshold.ThresholdDataItem controlItem = new org.moskito.control.core.threshold.ThresholdDataItem();
		controlItem.setLastValue(agentItem.getLastValue());
		controlItem.setName(agentItem.getName());
		controlItem.setStatus(HealthColor.getHealthColor(agentItem.getStatus()));
		controlItem.setStatusChangeTimestamp(agentItem.getStatusChangeTimestamp());
		return controlItem;
	}

	private AccumulatorDataItem agent2control(org.moskito.controlagent.data.accumulator.AccumulatorDataItem agentItem){
		AccumulatorDataItem controlItem = new AccumulatorDataItem(agentItem.getTimestamp(), agentItem.getValue());
		return controlItem;
	}

	public boolean supportsInfo(){
		return true;
	}

	public boolean supportsThresholds(){
		return true;
	}

	public boolean supportsAccumulators(){
		return true;
	}

	public static void main(String[] args){
		RMIConnector connector = new RMIConnector();
		connector.configure("localhost:9450", "lol");

		List<ProducerInfo> producerInfos = connector.getProducers();
		int lol = 5;

	}

}
