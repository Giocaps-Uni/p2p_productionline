# P2P Production line control system

Dependencies are in maven's pom.xml file

To launch each peer, issue this command on a terminal

path-to-java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
-p path-to-workspace/p2p_production_line/target/classes:path-to-jgroups.jar -XX:+ShowCodeDetailsInExceptionMessages -m p2p_production_line/p2p_production_line.Peer machinename

example

/home/giovanni/.p2/pool/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.linux.x86_64_17.0.9.v20231028-0858/jre/bin/java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8
-Dstderr.encoding=UTF-8 -p /home/giovanni/eclipse-workspace/p2p_production_line/target/classes:/home/giovanni/.m2/repository/org/jgroups/jgroups/5.0.0.Final/jgroups-5.0.0.Final.jar
-XX:+ShowCodeDetailsInExceptionMessages -m p2p_production_line/p2p_production_line.Peer inkjet
