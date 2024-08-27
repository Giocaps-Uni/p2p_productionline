package p2p_production_line;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.jgroups.*;
import org.jgroups.util.Util;




public class Peer {
	// Group of peers (the entire production line)
	protected JChannel ch;
	private String clusterName = "prod_line";
	private Timer timer;
	public class myMessage {
		String message;
		int count;
	}
	@SuppressWarnings("resource")
	
	protected void start(String name) throws Exception {
        ch = new JChannel("udp.xml").name(name)
          .setReceiver(new MyReceiver(name))
          .connect(clusterName);
        ch.setDiscardOwnMessages(true);
        // Logic 
    }
	
	
    protected class MyReceiver implements Receiver {
        protected final String name;
        protected MyReceiver(String name) {
            this.name=name;
        }

        public void receive(Message msg) {
            String mess = msg.getObject();
        	
            System.out.printf("-- [%s] msg from %s: %s\n", name, msg.src(), msg.getObject());
        	
        	if (mess.equals("Init")) {
        		try {
        			// Gets the current member position in the memberlist using the address
        			View v = ch.getView();
        			int pos = v.getMembers().indexOf(ch.getAddress());
        			// If it's the last machine
        			if (pos == v.size() -1 )
        				notifyAllReady(v);
        			else
        				forwardInit(ch.getView(), pos+1);
        		} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        	else if (mess.equals("AllReady"))
        		notifyStart();
        	
            
        }

        public void viewAccepted(View v) {
        	// Initialize system when correct number of machines is up
            // Necessary because only here the member number is updated
        	if(v.size() == 4)
				try {
					init(v);
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        }
    }
    
    private void init(View v) throws Exception{
    	try {
    	// Only the coordinator sends the init message to the next machine on the ring
    	if (ch.getAddress() == v.getCoord()) {
    		
    		ch.send(v.getMembers().get(1), "Init");
    		timer = new Timer();
			TimerTask task = new TimerTask() {
			    @Override
			    public void run() {
			        System.out.println("Times up");
			    }
			};
			long delay = TimeUnit.SECONDS.toMillis(10);
			timer.schedule(task, delay);
    	}
    	} catch (Exception e) {
    		//e.printStackTrace();
    	}
    }
    private void forwardInit(View v, int position) throws Exception {
    	try {
    		Util.sleep(1000);
    		ch.send(v.getMembers().get(position), "Init");
    	} catch (Exception e) {
    		e.printStackTrace();
			}
    }
    
    private void notifyAllReady(View v) {
    	try {
    		ch.send(v.getCoord(), "AllReady");
    	} catch (Exception e) {
    		e.printStackTrace();
			}
    }
    
    private void notifyStart() {
    	try {
    		timer.cancel();
    		ch.send(null, "Start");
    	} catch (Exception e) {
    		e.printStackTrace();
			}
    
    }
    	
      
    public static void main(String[] args) throws Exception {
        new Peer().start(args[0]);
    }
}
