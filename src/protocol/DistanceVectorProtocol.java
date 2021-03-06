package protocol;

import client.IRoutingProtocol;
import client.LinkLayer;
import client.Packet;

import java.util.*;

public class DistanceVectorProtocol implements IRoutingProtocol {
    private LinkLayer linkLayer;

    private HashMap<Integer, RoutingEntry> forwardingTable;
    private List<Integer> neighboursList;

    public DistanceVectorProtocol() {
        forwardingTable = new HashMap<>();
        neighboursList = new ArrayList<>();
    }

    @Override
    public void init(LinkLayer linkLayer) {
        this.linkLayer = linkLayer;
        forwardingTable.put(linkLayer.getOwnAddress(), new RoutingEntry(linkLayer.getOwnAddress(), 0, linkLayer.getOwnAddress()));
    }


    @Override
    public void tick(Packet[] packets) {
//        if (new Random().nextFloat() < 0.9) {
//            System.out.print("tick; ");
//        } else {
//            System.out.print("tock; ");
//        }
//        System.out.println("received " + packets.length + " packets");

        updateKnownNeighbours(packets);
        checkForDeadNeighbours(packets);
        updateForwardingTableFromReceivedPackets(packets);
        if (forwardingTable.size() == 1) {
            broadcastEmptyPacket();
        } else {
            sendTableToKnownNeighbours();
        }
    }

    private void checkForDeadNeighbours(Packet[] packets) {

        if (packets.length < neighboursList.size()) {
            boolean alive = false;
            for (Map.Entry<Integer,RoutingEntry> neighbour : forwardingTable.entrySet()) {
                for (Packet packet : packets) {
                    if (neighbour.getValue().nextHop == packet.getSourceAddress())
                        alive = true;
                }
                if (!alive){
                    forwardingTable.put(neighbour.getKey(),forwardingTable.get(neighbour.getKey()).setCost(Integer.MAX_VALUE));
                }
            }
        }
    }

    private void sendTableToKnownNeighbours() {
        //Send a personalized forwarding table to every known neighbour
        for (int neighbour : neighboursList) {
            HashMap<Integer, RoutingEntry> personalizedForwardingTable = new HashMap<>(forwardingTable);
            //Checks if the table has routes via this neighbour. This entry won't be sent.
            for (HashMap.Entry<Integer, RoutingEntry> personalizedEntry : forwardingTable.entrySet()) {
                if (personalizedEntry.getValue().nextHop == neighbour || personalizedEntry.getKey() == neighbour) {
                    personalizedForwardingTable.remove(personalizedEntry.getKey());
                }
            }
            System.out.println("Sending ");
            personalizedForwardingTable.entrySet().forEach(entry -> System.out.println(entry.getKey() + ">" + entry.getValue().nextHop + "(" + entry.getValue().cost + ")"));
            System.out.println("to " + neighbour);
            //Send
            linkLayer.transmit(new Packet(linkLayer.getOwnAddress(), neighbour, Util.serializeRoutingTable(personalizedForwardingTable)));
        }
    }

    private void broadcastEmptyPacket() {
        //Broadcast an empty packet to all neighbours to notify them of its existence
        linkLayer.transmit(new Packet(linkLayer.getOwnAddress(), 0, new byte[0]));
    }

    private void updateForwardingTableFromReceivedPackets(Packet[] packets) {
        for (Packet packet : packets) {
            if (packet.getRawData().length == 0)
                continue;
            HashMap<Integer, RoutingEntry> receivedTable = (HashMap<Integer, RoutingEntry>) Util.getForwardingTableFromPacket(packet);

            for (Map.Entry<Integer, RoutingEntry> entry : receivedTable.entrySet()) {
                RoutingEntry myEntry = new RoutingEntry(packet.getSourceAddress(), linkLayer.getLinkCost(packet.getSourceAddress()) + entry.getValue().cost, entry.getKey());
                if (forwardingTable.containsKey(myEntry.finalDestination)) {
                    if (forwardingTable.get(myEntry.finalDestination).cost > myEntry.cost || myEntry.cost == Integer.MAX_VALUE) {
                        forwardingTable.remove(myEntry.finalDestination);
                        forwardingTable.put(myEntry.finalDestination, myEntry);
                    }
                } else {
                    forwardingTable.put(myEntry.finalDestination, myEntry);
                }
            }
        }
    }


    private void updateKnownNeighbours(Packet[] packets) {
        for (Packet packet : packets) {
            int sourceAddress = packet.getSourceAddress();
            if (!neighboursList.contains(sourceAddress)) {
                neighboursList.add(sourceAddress);
                forwardingTable.put(sourceAddress, new RoutingEntry(sourceAddress, linkLayer.getLinkCost(sourceAddress), sourceAddress));
            }
        }
        List<Integer> disconnectedNeighbours = new ArrayList<>();
        for (Integer neighbour : neighboursList){
            if (Arrays.stream(packets).noneMatch(packet -> packet.getSourceAddress() == neighbour)) {
                //Then a node disconnected
                disconnectedNeighbours.add(neighbour);
                List<Integer> unknownDestinations = new ArrayList<>();
                for (Map.Entry<Integer, RoutingEntry> entry : forwardingTable.entrySet()){
                    if (entry.getValue().nextHop == neighbour) {
                        unknownDestinations.add(entry.getKey());
                    }
                }
                for (int destination : unknownDestinations){
                    forwardingTable.remove(destination);
                }
            }
        }
        for (Integer neighbour : disconnectedNeighbours){
            neighboursList.remove(neighbour);
        }
    }

    public HashMap<Integer, Integer> getForwardingTable() {
        // This code transforms your forwarding table which may contain extra information
        // to a simple one with only a next hop (value) for each destination (key).
        // The result of this method is send to the server to validate and score your protocol.

        // <Destination, NextHop>
        HashMap<Integer, Integer> ft = new HashMap<>();

        for (Map.Entry<Integer, RoutingEntry> entry : forwardingTable.entrySet()) {
            ft.put(entry.getKey(), entry.getValue().nextHop);
        }

        return ft;
    }
}
