package ev3;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ajax.JSON;

import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.LoginResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectorConfig;

import lejos.nxt.Button;
import lejos.nxt.LCD;
import lejos.nxt.Motor;

/** 
 * Demonstration Java program to run on the Lego Mindstorms EV3 receiving commands
 *   via Salesforce using the Streaming API and records being inserted into a Custom Object
 */
public class EV3ForceCommand {

    public static void main(String[] args)
    	throws Exception
    {
    	// Login
        LCD.clear();
        System.out.println("Logging in....");
        LCD.drawString("Logging in....", 0, 5);
        ConnectorConfig partnerConfig = new ConnectorConfig();
        partnerConfig.setManualLogin(true); // Manual configuration to get LoginResult
        partnerConfig.setServiceEndpoint("https://login.salesforce.com/services/Soap/u/29.0");
        final PartnerConnection partnerConnection = Connector.newConnection(partnerConfig);
        final LoginResult loginResult = partnerConnection.login(args[0], args[1]); 
		partnerConfig.setServiceEndpoint(loginResult.getServerUrl());
        partnerConfig.setSessionId(loginResult.getSessionId());
        partnerConnection.setSessionHeader(loginResult.getSessionId());        
        
        // Subscribe to the Command push topic
        LCD.clear();
        System.out.println("Stream connect....");
        LCD.drawString("Stream connect....", 0, 5);        
        final BayeuxClient client = makeStreamingAPIConnection(loginResult);

        // Configure robot
    	Motor.B.setSpeed(900);
    	Motor.C.setSpeed(900);
        
        // Subscribe to the Process__c topic to listen for new or updated Processes
        client.getChannel("/topic/commands").subscribe(new ClientSessionChannel.MessageListener() 
            {
                @SuppressWarnings("unchecked")
                public void onMessage(ClientSessionChannel channel, Message message) 
                {
                    try
                    {
                        HashMap<String, Object> data = (HashMap<String, Object>) JSON.parse(message.toString());
                        HashMap<String, Object> record = (HashMap<String, Object>) data.get("data");
                        HashMap<String, Object> sobject = (HashMap<String, Object>) record.get("sobject");
                        System.out.println("Command " + sobject);
                        String commandName = (String) sobject.get("Name");
                        String command = (String) sobject.get("Command__c");
                        String commandParameter = (String) sobject.get("CommandParameter__c");
                        String programToRunId = (String) sobject.get("ProgramToRun__c");
                        executeCommand(commandName, command, commandParameter, programToRunId, partnerConnection);
                    }
                    catch (Exception e)
                    {
                    	System.err.println(e.getMessage());
                    	System.exit(1);
                    }
                }
            });          
        
        // Press button to stop
        Button.waitForAnyPress();
        System.exit(1);;
    }
    
    /**
     * Executes commands received from Salesforce via the Streaming API
     * @param commandName
     * @param command
     * @param commandParameter
     * @param programToRun
     */
    private static void executeCommand(String commandName, String command, String commandParameter, String programToRunId, PartnerConnection partnerConnection)
    	throws Exception
    {
        LCD.clear();    	
        LCD.drawString("Running:  ", 0, 1);
        LCD.drawString(commandName, 0, 2);
        LCD.drawString("Command:  ", 0, 3);
        LCD.drawString(command, 0, 4);
        LCD.drawString("Parameter:", 0, 5);        
        LCD.drawString(commandParameter, 0, 6);        
        if(command.equals("Forward"))
        {
        	// Run both motors forward for a given time and then cut power to them        	
        	Motor.B.forward();
        	Motor.C.forward();
        	if(commandParameter!=null && commandParameter.length()>0)
        		Thread.sleep(Long.parseLong(commandParameter) * 1000);
        	Motor.B.flt(true);
        	Motor.C.flt();
        }
        else if(command.equals("Backward"))
        {
        	// Run both motors backward for a given time and then cut power to them
        	Motor.B.backward();
        	Motor.C.backward();
        	if(commandParameter!=null && commandParameter.length()>0)
        		Thread.sleep(Long.parseLong(commandParameter) * 1000);
        	Motor.B.flt(true);
        	Motor.C.flt();
        }                            	
        else if(command.equals("Rotate"))
        {
        	// Run both motors in opposite directions for a given time and then cut power to them        	
        	Motor.B.forward();
        	Motor.C.backward();
        	if(commandParameter!=null && commandParameter.length()>0)
        		Thread.sleep(Long.parseLong(commandParameter) * 1000);
        	Motor.B.flt(true);
        	Motor.C.flt();
        }                            	                            	
        else if(command.equals("Stop"))
        {
        	// Stop both motors (cut power and let them float)
        	Motor.B.flt(true);
        	Motor.C.flt();
        }        
        else if(command.equals("Shutdown"))
        {
        	// Power the robot down
        	Runtime.getRuntime().exec("/sbin/halt");
        	System.exit(1);
        }                
        else if(command.equals("Run Program"))
        {
        	// Query for the given program commands and execute them as above
			QueryResult result = 
				partnerConnection.query(
					"select Name, Command__c, CommandParameter__c, ProgramToRun__c " +
					  "from Command__c " +
					  "where Program__c = '" + programToRunId + "' order by Name");
			SObject[] commands = result.getRecords();
			int loopProgram = 1;
			if(commandParameter!=null && commandParameter.length()>0)
				loopProgram = Integer.parseInt(commandParameter);
			for(int loop=0; loop<loopProgram; loop++)
				for(SObject commandRecord : commands)
					executeCommand(
							(String) commandRecord.getField("Name"),
							(String) commandRecord.getField("Command__c"),
							(String) commandRecord.getField("CommandParameter__c"),
							(String) commandRecord.getField("ProgramToRun__c"),
							partnerConnection);
        }
    }
    
    /**
     * Uses the Jetty HTTP Client and Cometd libraries to connect to Saleforce Streaming API
     * @param config
     * @return
     * @throws Exception
     */
	private static BayeuxClient makeStreamingAPIConnection(LoginResult loginResult) 
	        throws Exception 
	{
	    HttpClient httpClient = new HttpClient();
	    httpClient.setConnectTimeout(20 * 1000); // Connection timeout
	    httpClient.setTimeout(120 * 1000); // Read timeout
	    httpClient.start();
	
	    // Determine the correct URL based on the Service Endpoint given during logon
	    URL soapEndpoint = new URL(loginResult.getServerUrl());
	    StringBuilder endpointBuilder = new StringBuilder()
	        .append(soapEndpoint.getProtocol())
	        .append("://")
	        .append(soapEndpoint.getHost());
	    if (soapEndpoint.getPort() > 0) endpointBuilder.append(":")
	        .append(soapEndpoint.getPort());
	    String endpoint = endpointBuilder.toString();
	    
	    // Ensure Session ID / oAuth token is passed in HTTP Header
	    final String sessionid = loginResult.getSessionId();
	    Map<String, Object> options = new HashMap<String, Object>();
	    options.put(ClientTransport.TIMEOUT_OPTION, httpClient.getTimeout());
	    LongPollingTransport transport = new LongPollingTransport(options, httpClient) 
	            {
	                    @Override    
	                protected void customize(ContentExchange exchange) 
	                    {
	                    super.customize(exchange);
	                    exchange.addRequestHeader("Authorization", "OAuth " + sessionid);
	                }
	            };
	
	    // Construct Cometd BayeuxClient
	    BayeuxClient client = new BayeuxClient(new URL(endpoint + "/cometd/29.0").toExternalForm(), transport);
	            
	    // Add listener for handshaking
	    client.getChannel(Channel.META_HANDSHAKE).addListener
	        (new ClientSessionChannel.MessageListener() {
	
	        public void onMessage(ClientSessionChannel channel, Message message) {
	
	                System.out.println("[CHANNEL:META_HANDSHAKE]: " + message);
	
	            boolean success = message.isSuccessful();
	            if (!success) {
	                String error = (String) message.get("error");
	                if (error != null) {
	                    System.out.println("Error during HANDSHAKE: " + error);
	                    System.out.println("Exiting...");
	                    System.exit(1);
	                }
	
	                Exception exception = (Exception) message.get("exception");
	                if (exception != null) {
	                    System.out.println("Exception during HANDSHAKE: ");
	                    exception.printStackTrace();
	                    System.out.println("Exiting...");
	                    System.exit(1);
	
	                }
	            }
	        }
	
	    });
	
	    // Add listener for connect
	    client.getChannel(Channel.META_CONNECT).addListener(
	        new ClientSessionChannel.MessageListener() {
	        public void onMessage(ClientSessionChannel channel, Message message) {
	
	            System.out.println("[CHANNEL:META_CONNECT]: " + message);
	
	            boolean success = message.isSuccessful();
	            if (!success) {
	                String error = (String) message.get("error");
	                if (error != null) {
	                    System.out.println("Error during CONNECT: " + error);
	                    System.out.println("Exiting...");
	                    System.exit(1);
	                }
	            }
	        }
	
	    });
	
	    // Add listener for subscribe
	    client.getChannel(Channel.META_SUBSCRIBE).addListener(
	        new ClientSessionChannel.MessageListener() {
	
	        public void onMessage(ClientSessionChannel channel, Message message) {
	
	                System.out.println("[CHANNEL:META_SUBSCRIBE]: " + message);
	            boolean success = message.isSuccessful();
	            if (!success) {
	                String error = (String) message.get("error");
	                if (error != null) {
	                    System.out.println("Error during SUBSCRIBE: " + error);
	                    System.out.println("Exiting...");
	                    System.exit(1);
	                }
	            }
	        }
	    });
	
	    // Begin handshaking
	    client.handshake();
	    System.out.println("Waiting for handshake....");        
	    boolean handshaken = client.waitFor(60 * 1000, BayeuxClient.State.CONNECTED);
	    if (!handshaken) {
	        System.out.println("Failed to handshake: " + client);
	        System.exit(1);
	    }
	
	    return client;
	}    
}
