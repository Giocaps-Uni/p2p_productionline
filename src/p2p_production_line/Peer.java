package p2p_production_line;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.Random;

import org.jgroups.*;
import org.jgroups.util.Util;


public class Peer {
	// Group of peers (the entire production line)
	protected JChannel ch;
	private String clusterName = "prod_line";
	
	private Timer timer;
	
	private int machineNum = 4;
	private int workingCycles = 2;
	
	// Buffer of machines
	private int buffer = 0;
	
	// First machine is a press and determines the tile size (width)
	private int tile_size = 10;
	
	// Second machine is the Inkjet and determines how much ink to spray
	private int ink_amount = 20;
	
	// Third machine is the oven and decides the conveyor belt speed that pass inside the oven
	private int roller_speed = 15;
	
	// Fourth machine is the cutter and cuts the tiles according to the oven speed
	private int cut_lenght = 10;
	
	
	@SuppressWarnings("resource")
	
	protected void start(String name) throws Exception {
        
		// Connect to the production line cluster using node addres as channel name 
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

        public void receive(Message msg) {
            
        	String mess = msg.getObject();
        	
            System.out.printf("-- [%s] msg from %s: %s\n", name, msg.src(), msg.getObject());
            View v = ch.getView();
            int src = v.getMembers().indexOf(msg.src());
            // Gets the current member position in the memberlist using the address
            int pos = v.getMembers().indexOf(ch.getAddress());
            
            // INIT
        	if (mess.equals("Init")) {
        		try {
        			
        			// If it's the last machine
        			if (pos == v.size() -1 )
        				notifyAllReady(v, "AllReady");
        			
        			else
        				forwardInit(ch.getView(), pos+1, "Init");
        		
        		} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        	
        	// All machines ready for production
        	else if (mess.equals("AllReady"))
        		notifyStart();
        	
        	// Other nodes
        	else if (mess.equals("Start"))
				try {
					inProduction(v, pos);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	
        	// Other nodes
        	else if (mess.equals("Stop"))
        	{
        		try {
        			for (int j = buffer; j>0; j--)
    	    			Util.sleep(1000);
        			// If it's the last machine
        			if (pos == v.size() - 1 ) {
        				
        				notifyAllReady(v, "AllStop");
	        			System.out.print("Stopping");
	        		    //System.exit(0);
        			}
        			else
        				forwardInit(ch.getView(), pos+1, "Stop");
        		
        		} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        	// Coordinator
        	else if (mess.equals("AllStop"))
        	{
        		System.out.print("Stopping");
    		    //System.exit(0);
        	}
        	else if (mess.equals("Calib")) {
        		try {
					sendVal(v, pos);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
            
        }
        
        // Callback called when a new node enters the cluster
        public void viewAccepted(View v) {
        	// Initialize system when correct number of machines is up
            // Necessary because only here the member number is updated for each node
        	// Otherwise only the last node sees the correct number
        	if(v.size() == machineNum)
				try {
					// Here each node will launch the procedure but only the first machine will execute the code inside it
					// Necessary because the channel initialization is not assured to be done as the first step and 
					// java returns a null pointer exception when trying to access the variable ch
					
					init(v, "Init");
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        }
    }
    
    // First machine forwards the token init and starts a timer to keep track of the elapsed time and notify the user for problems
    private void init(View v, String m) throws Exception{
    	try {
    	// Done only by the coordinator 
    	if (ch.getAddress() == v.getCoord()) {
    		// sends the init message to the next machine on the ring, which for sure is position number 1 in the memberlist
    		ch.send(v.getMembers().get(1), m);
    		
    		// Instantiates timer and launch
    		if (m.equals("Init")){
    		timer = new Timer();
			TimerTask task = new TimerTask() {
			    @Override
			    public void run() {
			        System.out.println("A machine did not respond, please check");
			    }
			};
			// Timeout after (2 times the number of nodes) seconds
			long delay = TimeUnit.SECONDS.toMillis(2*v.size());
			timer.schedule(task, delay);
    		}
    	}
    	} catch (Exception e) {
    		//e.printStackTrace();
    	}
    }
    // Each entity will forward the init to the one next to it, sleep 1 second for debugging purposes
    private void forwardInit(View v, int position, String m) throws Exception {
    	try {
    		Util.sleep(1000);
    		
    		if (position < v.size())
    			ch.send(v.getMembers().get(position), m);
    		
    		if (m.equals("Stop")) {
    			
    			System.out.print("Stopping");
    		    //System.exit(0);
    		}
    	} catch (Exception e) {
    		e.printStackTrace();
			}
    }
    
    // Send init token back to the coordinator
    private void notifyAllReady(View v, String m) {
    	try {
    		ch.send(v.getCoord(), m);
    		
    	} catch (Exception e) {
    		e.printStackTrace();
			}
    }
    
    // Necessary to broadcast the start production nofification on the system and to reset the  coordinator timer 
    private void notifyStart() {
    	try {
    		timer.cancel();
    		// broadcast to everyone, simplyfing assumption: otherwise another token should be forwarded to each machine,
    		// possibly creating a loop of infinite token passing
    		ch.send(null, "Start");
    		inProduction(ch.getView(), 0);
    	} catch (Exception e) {
    		e.printStackTrace();
			}
    
    }
    
    // Infinite loop for production stage -> calibration + balancing 
    private void inProduction(View v, int pos) throws Exception {
    	// Production Buffer of the machine
    	
    	
    	for (int i=0; i < workingCycles; i++) {
    		
    		Util.sleep(1000);
    		
	    	// First machine -> Press
	    	// Parameter: width of the tiles
	    	if (pos == 0) {
	    		buffer++;
	    		// Every 2 cycles randomly update the tile width
	    		if (i % 2 == 0) {
	    			Random rand = new Random();
	    			int val = rand.nextInt(1);
	    			if (val == 0)
	    				tile_size++;
	    			else
	    				if (tile_size > 1)
	    					tile_size--;
	    				else 
	    					tile_size++;
	    		}
	    		
	    	}
	    	
	    	// Second machine -> ink injector
	    	// Parameter: amount of ink to spray, proportional to tile width
	    	else if (pos == 1) {
	    		buffer = 2;
	    		Util.sleep(1000+pos*1000);
	    		ch.send(v.getMembers().get(pos-1), "Calib");
	    		
	    	}
	    	
	    	// Third machine -> Oven
	    	// Parameters: temperature, depends on the amount of ink to dry
	    	else if (pos == 2) {
	    		buffer = 3;

	    		Util.sleep(1000+pos*1000);
	    		ch.send(v.getMembers().get(pos-1), "Calib");
	    	}
	    	
	    	// Fourth machine -> Cutter
	    	// Parameters: depend on the tile format, ask to first machine
	    	else {
	    		buffer = 2;

	    		Util.sleep(1000+pos*1000);
	    		ch.send(v.getMembers().get(pos-1), "Calib");
	    		
	    	}
    	}
    	// After working cycles are finished
    	
		if (pos == 0) {
			// Empty buffer
    		for (int j = buffer; j>0; j--)
    			Util.sleep(1000);
		    init(v, "Stop");
		    
		}
    }
    
    private void sendVal(View v, int pos) throws Exception {
    	int value;
    	if (pos == 0)   		
    		value = tile_size;
    	else if (pos == 1)
    		value = ink_amount;
    	else if (pos == 2)
    		value = roller_speed;
    	else
    		value = tile_size;
    	ch.send(v.getMembers().get(pos+1), "val" + value);
    }
      
    public static void main(String[] args) throws Exception {
    	// Launch peer with address = argument
        new Peer().start(args[0]);
    }
}
