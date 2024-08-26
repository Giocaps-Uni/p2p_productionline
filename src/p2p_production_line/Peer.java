package p2p_production_line;

import org.jgroups.*;
import org.jgroups.util.Util;

public class Peer {
	protected JChannel ch;
	@SuppressWarnings("resource")
	protected void start(String name) throws Exception {
        ch = new JChannel("udp.xml").name(name)
          .setReceiver(new MyReceiver(name))
          .connect("demo-cluster");
        int counter=1;
        for(;;) {
            ch.send(null, "msg-" + counter++);
            Util.sleep(3000);
        }
    }

    protected static class MyReceiver implements Receiver {
        protected final String name;

        protected MyReceiver(String name) {
            this.name=name;
        }

        public void receive(Message msg) {
            System.out.printf("-- [%s] msg from %s: %s\n", name, msg.src(), msg.getObject());
        }

        public void viewAccepted(View v) {
            System.out.printf("-- [%s] new view: %s\n", name, v);
        }
    }

    public static void main(String[] args) throws Exception {
        new Peer().start(args[0]);
    }
}
