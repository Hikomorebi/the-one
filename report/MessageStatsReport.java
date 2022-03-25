/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.NetworkInterface;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.World;
import interfaces.SimpleBroadcastInterface;

/**
 * Report for generating different kind of total statistics about message
 * relaying performance. Messages that were created during the warm up period
 * are ignored.
 * <P>
 * <strong>Note:</strong> if some statistics could not be created (e.g. overhead
 * ratio if no messages were delivered) "NaN" is reported for double values and
 * zero for integer median(s).
 */
public class MessageStatsReport extends Report implements MessageListener {
	private Map<String, Double> creationTimes;
	private List<Double> latencies;
	private List<Integer> hopCounts;
	private List<Double> msgBufferTime;
	private List<Double> rtt; // round trip times

	private int nrofDropped;
	private int nrofRemoved;
	private int nrofStarted;
	private int nrofAborted;
	private int nrofRelayed;
	private int nrofCreated;
	private int nrofResponseReqCreated;
	private int nrofResponseDelivered;
	private int nrofDelivered;

	/**
	 * Constructor.
	 */
	public MessageStatsReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		this.creationTimes = new HashMap<String, Double>();
		this.latencies = new ArrayList<Double>();
		this.msgBufferTime = new ArrayList<Double>();
		this.hopCounts = new ArrayList<Integer>();
		this.rtt = new ArrayList<Double>();

		this.nrofDropped = 0;
		this.nrofRemoved = 0;
		this.nrofStarted = 0;
		this.nrofAborted = 0;
		this.nrofRelayed = 0;
		this.nrofCreated = 0;
		this.nrofResponseReqCreated = 0;
		this.nrofResponseDelivered = 0;
		this.nrofDelivered = 0;
	}

	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		if (isWarmupID(m.getId())) {
			return;
		}

		if (dropped) {
			this.nrofDropped++;
		} else {
			this.nrofRemoved++;
		}

		this.msgBufferTime.add(getSimTime() - m.getReceiveTime());
	}

	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofAborted++;
	}

	public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean finalTarget) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofRelayed++;
		if (finalTarget) {
			this.latencies.add(getSimTime() - this.creationTimes.get(m.getId()));
			this.nrofDelivered++;
			this.hopCounts.add(m.getHops().size() - 1);

			if (m.isResponse()) {
				this.rtt.add(getSimTime() - m.getRequest().getCreationTime());
				this.nrofResponseDelivered++;
			}
		}
	}

	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getId());
			return;
		}

		this.creationTimes.put(m.getId(), getSimTime());
		this.nrofCreated++;
		if (m.getResponseSize() > 0) {
			this.nrofResponseReqCreated++;
		}
	}

	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getId())) {
			return;
		}

		this.nrofStarted++;
	}

	@Override
	public void done() {
		System.out.println(format(getSimTime()));

	}

	@Override
	public void done1(World world) {
		String saveFile = "reports/out.txt";
		File file = new File(saveFile);
		FileOutputStream fos = null;
		OutputStreamWriter osw = null;

		try {
			if (!file.exists()) {
				boolean hasFile = file.createNewFile();
				if (hasFile) {
				}
				fos = new FileOutputStream(file);
			} else {
				fos = new FileOutputStream(file, true);
			}

			osw = new OutputStreamWriter(fos, "utf-8");
			osw.write("Message stats for scenario " + getScenarioName() + "\nsim_time: " + format(getSimTime())); // 写入内容
			double deliveryProb = 0; // delivery probability
			double responseProb = 0; // request-response success probability
			double overHead = Double.NaN; // overhead ratio

			if (this.nrofCreated > 0) {
				deliveryProb = (1.0 * this.nrofDelivered) / this.nrofCreated;
			}
			if (this.nrofDelivered > 0) {
				overHead = (1.0 * (this.nrofRelayed - this.nrofDelivered)) / this.nrofDelivered;
			}
			if (this.nrofResponseReqCreated > 0) {
				responseProb = (1.0 * this.nrofResponseDelivered) / this.nrofResponseReqCreated;
			}
			

//			String statsText = "\ncreated: " + this.nrofCreated + "\nstarted: " + this.nrofStarted + "\nrelayed: "
//					+ this.nrofRelayed + "\naborted: " + this.nrofAborted + "\ndropped: " + this.nrofDropped
//					+ "\nremoved: " + this.nrofRemoved + "\ndelivered: " + this.nrofDelivered + "\ndelivery_prob: "
//					+ format(deliveryProb) + "\nresponse_prob: " + format(responseProb) + "\noverhead_ratio: "
//					+ format(overHead) + "\nlatency_avg: " + getAverage(this.latencies) + "\nlatency_med: "
//					+ getMedian(this.latencies) + "\nhopcount_avg: " + getIntAverage(this.hopCounts)
//					+ "\nhopcount_med: " + getIntMedian(this.hopCounts) + "\nbuffertime_avg: "
//					+ getAverage(this.msgBufferTime) + "\nbuffertime_med: " + getMedian(this.msgBufferTime)
//					+ "\nrtt_avg: " + getAverage(this.rtt) + "\nrtt_med: " + getMedian(this.rtt);
			String myText = "";
			String mySpeed = "";
			int count = 0, maxcount = -1;
			HashMap<String, Integer> map = new HashMap<String, Integer>();
			for (DTNHost host : world.getHosts()) {
				if (host.getGroupId().equals("core")) {
					for (Connection connectionBS : host.getConnections()) {
						count = 0;
						myText += (connectionBS.getOtherNode(host).getName() + ":");
						for (Connection connectionUser : connectionBS.getOtherNode(host).getConnections()) {
							if (connectionUser.getOtherNode(connectionBS.getOtherNode(host)).getGroupId()
									.equals("user")) {
								myText += ("\t"
										+ connectionUser.getOtherNode(connectionBS.getOtherNode(host)).getName());
								count++;
							}
						}
						map.put(connectionBS.getOtherNode(host).getName(), count);
						myText += "\n该基站连接的user个数为：" + count + "\n\n";
					}
				}
			}
			myText += "覆盖user数目最多的基站：";
			boolean isMax = true;
			for (String s : map.keySet()) {
				isMax = true;
				for (String t : map.keySet()) {
					if (map.get(s) < map.get(t)) {
						isMax = false;
						break;
					}
				}
				if (isMax) {
					myText += s;
				}
			}
			myText += "\n";
//			for(DTNHost host:world.getHosts()) {
//				if(host.getGroupId().equals("bs")) {
//					myText+=(host.getName()+":");
//					for(Connection connection: host.getConnections()) {
//						if(connection.getToNode().getGroupId().equals("user")) {
//							myText+=(connection.getToNode().getName()+"\t");
//						}
//					}
//					myText+="\n";
//				}
//			}
			for (DTNHost host : world.getHosts()) {
				if (host.getName().equals("bs106")) {
					mySpeed += ("基站7：" + host.getName() + "\n");
					for (Connection connection : host.getConnections()) {
						if (connection.getToNode().getGroupId().equals("user")) {
							mySpeed += ("\t" + connection.getToNode().getName() + "  " + "\t");
							int BW = (int) Math.floor((double) SimpleBroadcastInterface.transmitSpeed);
							double dis = host.getLocation().distance(connection.getToNode().getLocation());
							double r = BW * Math.log(1 + 5 / dis) / Math.log(2);
							r = r / 1000;
							mySpeed += "速率 = " + r + "Kb/s" + "\n";
						}
					}
					break;
				}

			}
			mySpeed+="\n";
			String routeText = "\nuser1给user10通信所经过的基站序列";
//			
			int countBS = 0;
			if (World.userOneBS.size() == 0 || World.userOneBS.size() == 0) {
				routeText += "不存在";
			} else {
				HashSet<Integer> oneBS = new HashSet<Integer>();
				for (Integer i1 : World.userOneBS) {
					for (Integer i2 : World.userTenBS) {
						if (i1 == i2) {
							oneBS.add(i1);
						}
					}
				}
				if (oneBS.size() == 0) {
					countBS = World.userOneBS.size();
					routeText += "可能为：user1->";
					countBS = World.userOneBS.size();
					for (Integer i : World.userOneBS) {
						routeText += "bs" + i;
						if (countBS-- > 1) {
							routeText += "/";
						}
					}
					routeText += "->";
					countBS = World.userTenBS.size();
					for (Integer i : World.userTenBS) {
						routeText += "bs" + i;
						if (countBS-- > 1) {
							routeText += "/";
						}
					}
					routeText += "->user10";

				} else {
					routeText += "可能为:user1->";
					countBS = oneBS.size();
					for (Integer i : oneBS) {
						routeText += "bs" + i;
						if (countBS-- > 1) {
							routeText += "/";
						}
					}
					routeText += "->user10\n也可能为:user1->";
					countBS = World.userOneBS.size();
					for (Integer i : World.userOneBS) {
						if (!oneBS.contains(i)) {
							routeText += "bs" + i;
						}

						if (countBS-- > 1) {
							routeText += "/";
						}
					}
					routeText += "->";
					countBS = World.userTenBS.size();
					for (Integer i : World.userTenBS) {
						if (!oneBS.contains(i)) {
							routeText += "bs" + i;
						}

						if (countBS-- > 1) {
							routeText += "/";
						}
					}
					routeText += "->user10";

				}
			}
			routeText += "\n";
			//osw.write(statsText);
			osw.write("\r\n");
			osw.write(myText);
			osw.write(routeText);
			osw.write(mySpeed);

		} catch (Exception e) {
			e.printStackTrace();
		} finally { // 关闭流
			try {
				if (osw != null) {
					osw.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				if (fos != null) {
					fos.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
