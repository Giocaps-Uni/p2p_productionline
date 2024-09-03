package p2p_production_line;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.Random;

import org.jgroups.*;
import org.jgroups.util.Util;


public class Peer {
	
	// Channel used by the peer to send and receive messages
	protected JChannel ch;
	
	// Unique name of the cluster to which the peer connects.
	private String clusterName = "prod_line";
	
	// Used to check timeout for faulty machines
	private Timer timer, timerc;
	
	// Used to avoid sending messages if a machine receives the stop message from the previous one
	private boolean stopped = false;
	
	// Number of machines in the line. Needed by the leader to know when to send the init message
	private int machineNum = 4;
	
	// Actually used for debugging and testing purposes, in reality it should be an endless loop
	private int workingCycles = 20;
	
	// Buffer of the machine, used to check wether it's almost full (>8) or not.
	private int buffer = 0;
	
	// Used for debugging and testing purposes
	private int sleepTime = 1000;
	
	// Each peer holds every parameter. Logically useless, could instantiate only the needed variable for the correct machine
	// Since each peer has the same java program, for scope visibility of variables in other functions is necessary to allocate here
	private float tile_size = 10;
	private float ink_amount = 20;
	private float roller_speed = 15;
	private float cut_lenght = 10;
	
	
	@SuppressWarnings("resource")
	
	// Called in the main function when a peer is created
	protected void start(String name) throws Exception {
        
		// Connect to the production line cluster using node addres as channel name
		// the protocol used is udp -> faster messages
		ch = new JChannel("udp.xml").name(name)
          .setReceiver(new MyReceiver(name))
          .connect(clusterName);
        
		// Do not send messages to own node, reduce msg complexity
		ch.setDiscardOwnMessages(true);
   
    }
	
	// Implementation of the receiver interface
    protected class MyReceiver implements Receiver {
        
    	protected final String name;
        
    	// Default constructor
    	protected MyReceiver(String name) {
            this.name=name;
        }
    	
    	// OnReceive callback, actions to be performed upon the reception of a message
        public void receive(Message msg) {
            
        	// Retrieve the content of the message (only strings circulate in the cluster)
        	String mess = msg.getObject();
        	
        	// Print only if it's not stopped
        	if (stopped == false)
        		System.out.printf("-- [%s] msg from %s: %s\n", name, msg.src(), mess);
            
        	// Necessary to get positions of the members in the cluster view
            View v = ch.getView();
            
            // Gets the current member position in the memberlist using its address (example: extruder (leader) is position 0, 
            // inkjet is position 1 etc. This implies that peers must be created in order following the real position in the line
            int pos = v.getMembers().indexOf(ch.getAddress());
            
            // INIT message received from the previous machine
        	if (mess.equals("Init")) {
        		try {
        			
        			// If it's the last machine in the line, notify the leader that each machine is ready, otherwise forward the
        			// init message to the next machine
        			if (pos == v.size() -1 )
        				notifyAllReady(v, "AllReady");
        			
        			else
        				forwardInit(ch.getView(), pos+1, "Init");
        		
        		} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        	
        	// All machines ready for production, broadcast the start message -> simultaneous start
        	// Only the leader will receive this message
        	else if (mess.equals("AllReady"))
        		notifyStart();
        	
        	// If a not leader node receives the start message, go in production
        	else if (mess.equals("Start"))
				try {
					inProduction(v, pos);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	
        	// If a not leader node receives the stop, empty the buffer and forward stop to other machines
        	else if (mess.equals("Stop"))
        	{
        		try {
        			stopped = true;
        			
        			// Empty buffer. Sleep time used to simulate real working conditions
        			System.out.print("Emptying buffer\n");
        			
        			for (int j = buffer; j>0; j--)
    	    			Util.sleep(1000);
        			
        			System.out.print("Done\n");
        			
        			// If it's the last machine notify the leader that every machine is stopped, otherwise forward stop to next machine
        			if (pos == v.size() - 1 ) {
        				
        				notifyAllReady(v, "AllStop");
	        			System.out.print("Stopping\n");
	        		    //System.exit(0);
        			}
        			else
        				forwardInit(ch.getView(), pos+1, "Stop");
        		
        		} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        	// Coordinator knows that every machine is stopped and can stop himself
        	else if (mess.equals("AllStop"))
        	{
        		System.out.print("All machines stopped\n");
    		    //System.exit(0);
        	}
        	
        	// On a calibration request respond with the correct value
        	else if (mess.equals("Calib")) {
        		try {
					sendVal(v, pos);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        	
        	// Slow down production speed if next machine's buffer is too full. Simulated increasing sleep time -> Longer working cycle
        	else if (mess.equals("Balance+")) {
        		sleepTime += 200;
        	}
        	// Increase production speed if previous machine's buffer is too full. Simulated decreasing sleep time -> Shorter working cycle
        	else if (mess.equals("Balance-")) {
        		if (sleepTime > 200)
        			sleepTime -= 200;
        	}
        	// Upon receiving the value from the previous machine, sets the new parameter according to which machine receives the message
        	else if (mess.startsWith("val")) {
        		if (stopped == false) {
	        		// Reset the timer started by requesting machine
        			timerc.cancel();
	        		float val = Float.parseFloat(mess.substring(3));
	        		
	        		// Second machine, Ink amount is double the width of the material
	        		if (pos == 1) {
	        			ink_amount = val*2;
	        			System.out.print("Updated Ink amount " + ink_amount +"\n");
	        		}
	        		// Third machine, oven's conveyor belt speed depends on the amount of ink to dry (more amount, less speed)
	        		else if (pos == 2) {
	        			roller_speed = 10/val;
	        			System.out.print("Updated Roller Speed " + roller_speed +"\n");
	        		}
	        		// Last machine, cut lenght depends directly on conveyor belt speed (frequency at which the cut is done)
	        		else {
	        			cut_lenght = val * 5;
	        			System.out.print("Updated Cut_lenght " + cut_lenght +"\n");
	        		}
        		}
        	}
            
        }
        
        // Callback called by the system when a new node enters the cluster
        public void viewAccepted(View v) {
        	// Initialize system when correct number of machines is up (4 in this case)
            // Necessary because only here the member number is updated for each node
        	// Otherwise only the last node sees the correct number
        	if(v.size() == machineNum)
				try {
					// Here each node will launch the procedure but only the first machine will execute the code inside it
					// Necessary because the channel initialization is not assured to be done as the first step and 
					// java returns a null pointer exception when trying to access the variable ch in this callback
					
					init(v, "Init");
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        }
    }
    
    // First machine forwards the token and starts a timer to keep track of the elapsed time and notify the user for problems
    private void init(View v, String m) throws Exception{
    	try {
    	// Done only by the coordinator (first machine)
    	if (ch.getAddress() == v.getCoord()) {
    		// sends the init message to the next machine on the ring, which obviously is position number 1 in the memberlist
    		ch.send(v.getMembers().get(1), m);
    		
    		// Instantiates timer only for init token
    		if (m.equals("Init")){
    		timer = new Timer();
			TimerTask task = new TimerTask() {
			    @Override
			    public void run() {
			    	// Called if the timer is not canceled
			        System.out.println("A machine did not respond to init, please check");
			    }
			};
			// Timeout after (2 times the number of nodes) seconds -> 8 seconds in this case
			long delay = TimeUnit.SECONDS.toMillis(2*v.size());
			timer.schedule(task, delay);
    		}
    	}
    	} catch (Exception e) {
    		//e.printStackTrace();
    	}
    }
    // Each entity will forward the message to the one next to it, sleep 1 second to simulate startup process
    private void forwardInit(View v, int position, String m) throws Exception {
    	try {
    		Util.sleep(1000);
    		
    		// If the peer is not the last machine, send to next one. Otherwise last machine try to send msg to a non-exixtent one
    		if (position < v.size())
    			ch.send(v.getMembers().get(position), m);
    		
    		if (m.equals("Stop")) {
    			// If the function is used for the stop procedure, notify the user. Otherwise do nothing, wait for start
    			System.out.print("Stopping\n");
    		    //System.exit(0);
    		}
    	} catch (Exception e) {
    		e.printStackTrace();
			}
    }
    
    // Used by last machine to send token back to the leader
    private void notifyAllReady(View v, String m) {
    	try {
    		ch.send(v.getCoord(), m);
    		
    	} catch (Exception e) {
    		e.printStackTrace();
			}
    }
    
    // Necessary to broadcast the start production nofification on the system and to reset the coordinator timer 
    private void notifyStart() {
    	try {
    		timer.cancel();
    		// Broadcast to everyone. Leader is assumed to have a direct connection to each machine to do this. 
    		// During the init procedure the token is circulated on the ring to check links between machines
    		ch.send(null, "Start");
    		// Start production in the leader machine
    		inProduction(ch.getView(), 0);
    	} catch (Exception e) {
    		e.printStackTrace();
			}
    
    }
    
    // Working loop for production stage -> calibration + balancing 
    private void inProduction(View v, int pos) throws Exception {
    	
    	for (int i = 0; i < workingCycles; i++) {
    		System.out.print("Sleep time " + sleepTime + "\n");
	    	// First machine does not have a buffer
	    	if (pos == 0) {
	    		if (stopped == false) {
	    		Util.sleep(sleepTime);
	    		// Every cycle update the width -> testing and debugging purposes, simulate a sensor reading a different value
	    		// or an user entering a different val
	    		tile_size++;
	    		}
	    	}
	    	
	    	// All other machines
	    	else {
	    		if (stopped == false) {
	    		Random rand = new Random();
	    		// Randomly update the buffer at each work cycle. Used to simulate the overflow, in reality it should be a value
	    		// read by a sensor
	    		if (buffer < 8) {
	    			buffer = buffer + rand.nextInt(5);
	    			if (buffer > 10)
	    				buffer = 10;
	    		}
	    		
	    		
	    		// Wait for amount of time proportional to the position of the machine in the line before asking for calibration
	    		// Done to avoid flooding of messages in the same time
	    		Util.sleep(sleepTime+pos*1000);
	    		ch.send(v.getMembers().get(pos-1), "Calib");
	    		timerc = new Timer();
				TimerTask task = new TimerTask() {
				    @Override
				    public void run() {
				        System.out.println("A machine did not respond to calibration, please check\n");
				    }
				};
				
				// Timeout after (2 times the number of nodes) seconds
				long delay = TimeUnit.SECONDS.toMillis(2*v.size());
				timerc.schedule(task, delay);	
				System.out.print("buffer " + buffer + "\n");
	    		}
	    	}
	    	
	    	// For debugging purposes
	    	Util.sleep(1000);
	    	
	    	// Empty buffer by one unit every working cycle
	    	if (buffer > 0)
	    		buffer--;
	    	
	    	// If buffer is more than 80% full, slow down previous machine and increase speed of next machine
	    	if (buffer >= 8)
	    		balance(v, pos);
    	}
    	
    	// After working cycles are finished coordinator sends the stop message on the ring. This to let machines empty their buffers
		if (pos == 0) {
			// Empty buffer of first machine and send stop message
			System.out.print("Emptying buffer\n");
    		for (int j = buffer; j>0; j--)
    			Util.sleep(1000);
    		System.out.print("Done. Sending stop message\n");
		    init(v, "Stop");
		    
		}
    }
    
    // Send balance messages to each neighbor.
    private void balance(View v, int pos) throws Exception {
    	// Tell previous machine to go slower
    	ch.send(v.getMembers().get(pos-1), "Balance+");
    	// Tell next machine to go faster. If its the last machine tell only the previous one
    	if (pos != v.size() - 1)
    		ch.send(v.getMembers().get(pos+1), "Balance-");
    }
    
    // Send correct value to peer requesting the calibration based on the position of the sender in the line
    private void sendVal(View v, int pos) throws Exception {
    	float value;
    	if (pos == 0)   		
    		value = tile_size;
    	else if (pos == 1)
    		value = ink_amount;
    	else if (pos == 2)
    		value = roller_speed;
    	// Last machine does not send values. Default assignment to cover each branch of the if
    	else
    		value = tile_size;
    	ch.send(v.getMembers().get(pos+1), "val" + value);
    }
      
    public static void main(String[] args) throws Exception {
    	// Launch peer with address = argument
        new Peer().start(args[0]);
    }
}
