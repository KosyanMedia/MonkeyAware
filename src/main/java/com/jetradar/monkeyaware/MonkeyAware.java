package com.jetradar.monkeyaware;

import org.apache.commons.net.util.SubnetUtils;
import org.xbill.DNS.TextParseException;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang.StringEscapeUtils.unescapeJava;

public class MonkeyAware {
    private static final String BLOCKED_HOSTS_URL = "https://reestr.rublacklist.net/api/current";
    private static final String QS = "\"";

    private static LineNumberReader getBlocked() throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(BLOCKED_HOSTS_URL).openConnection();
        conn.setRequestMethod("GET");
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder result = new StringBuilder();
        String line = lnr.readLine();
        while (line != null) {
            result.append(line).append("\n");
            line = lnr.readLine();
        }
        lnr.close();

        return new LineNumberReader(new StringReader(unescapeJava(result.toString())));
    }

    private static String[] toArray(String list) {
        String[] tmp = list.split(" | ");
        List<String> split = new ArrayList<>();
        for (String i : tmp)
            if (!i.equals("|"))
                split.add(i);
        return split.toArray(new String[split.size()]);
    }

    private static void readBlocked(Handler handler) throws IOException {
        LineNumberReader lnr = getBlocked();
        String line = lnr.readLine(), t;
        String[] tmp;
        List<String> split;
        while (line != null) {
            tmp = line.split(";");
            split = new ArrayList<>();
            t = "";
            for (String s : tmp) {
                if (t.startsWith(QS)) {
                    t = t + ";" + s;
                    if (s.endsWith(QS)) {
                        t = t.substring(1, t.length() - 1);
                        split.add(t);
                        t = "";
                    }
                } else if (s.startsWith(QS)) {
                    if (s.endsWith(QS) && s.length() > 1)
                        split.add(s.substring(1, s.length() - 1));
                    else
                        t = s;
                } else {
                    split.add(s);
                }
            }


            if (split.size() == 6) {
                handler.handle(toArray(split.get(0)),
                        toArray(split.get(1)),
                        toArray(split.get(2)),
                        split.get(3),
                        split.get(4),
                        split.get(5));
            }
            line = lnr.readLine();
        }
        lnr.close();
    }

    private static void report(String[] ips, String[] hosts, String[] pages, String who, String act, String date) {
        System.out.printf("%s %s %s %s %s %s\n",
                Arrays.toString(ips),
                Arrays.toString(hosts),
                Arrays.toString(pages),
                who, act, date);
    }

    private static boolean testIps(List<IpTester> ipList, String[] ips) {
        for (String ip : ips) {
            for (IpTester subnet : ipList)
                if (subnet.test(ip))
                    return true;
        }
        return false;
    }

    private static boolean testHosts(List<String> hostList, String[] hosts) {
        for (String host : hosts) {
            for (String domain : hostList)
                if (host.contains(domain))
                    return true;
        }
        return false;
    }

    private static List<IpTester> loadIpList(List<String> hostList) throws TextParseException, UnknownHostException {
        String[] ipList = new String[]{"88.85.75.165", "185.105.163.0/24", "31.192.117.0/24"};

        List<IpTester> list = new ArrayList<>();
        for (String ip : ipList) {
            list.add(new IpTester(ip));
        }

        for(String host : hostList){
            List<String> hostIpList = NsLookup.getHostsList(host);
            for(String hostIp : hostIpList)
                list.add(new IpTester(hostIp));
        }

        return list;
    }

    private static List<String> loadHostList() {
        return Arrays.asList("aviasales.ru", "jetradar.com", "pornhub.com");
    }

    public static void main(String... args) {
        try {
            List<String> hostList = loadHostList();
            List<IpTester> ipList = loadIpList(hostList);
            readBlocked((ips, hosts, pages, who, act, date) -> {
                if (testIps(ipList, ips) || testHosts(hostList, hosts))
                    report(ips, hosts, pages, who, act, date);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class IpTester {
        private String ip;
        private SubnetUtils.SubnetInfo subnet = null;

        IpTester(String ip) {
            this.ip = ip;
            if(ip.contains("/")){
                subnet = new SubnetUtils(ip).getInfo();
            }
        }

        boolean test(String ip){
            if(subnet != null){
                return subnet.isInRange(ip);
            }else{
                return ip.equals(this.ip);
            }
        }
    }

    private interface Handler {
        void handle(String[] ips, String[] hosts, String[] pages, String who, String act, String date);
    }
}
