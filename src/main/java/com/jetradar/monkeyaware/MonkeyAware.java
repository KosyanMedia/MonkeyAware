package com.jetradar.monkeyaware;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.net.util.SubnetUtils;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

    private static List<IpTester> loadIpList(String csvFile) throws IOException {
        List<IpTester> list = new ArrayList<>();
        for (String ip : getColumnValues(csvFile, 0)) {
            list.add(new IpTester(ip));
        }

        for (String host : getColumnValues(csvFile, 1)) {
            List<String> hostIpList = NsLookup.getHostsList(host);
            for (String hostIp : hostIpList)
                list.add(new IpTester(hostIp));
        }

        return list;
    }

    private static List<String> loadHostList(String csvFile) throws IOException {
        return getColumnValues(csvFile, 1);
    }

    private static List<String> getColumnValues(String csvFile, int column) throws IOException {
        List<String> result = new ArrayList<>();
        Reader in = new FileReader(csvFile);
        Iterable<CSVRecord> records = CSVFormat.RFC4180.parse(in);
        for (CSVRecord record : records) {
            if (column < record.size() && !record.get(column).trim().isEmpty()) {
                result.add(record.get(column));
            }
        }
        return result;
    }

    public static void main(String... args) {
        try {
            if (args.length < 2) {
                System.out.println("use com.jetradar.monkeyaware.MonkeyAware \\mode=[trigger|report] path/to/file.csv");
                System.exit(1);
            }
            boolean isTriggerMode = args[0].equals("\\mode=trigger");

            StringBuilder file = new StringBuilder();
            for (int i = 1; i < args.length; i++) file.append(args[i]);

            List<String> hostList = loadHostList(file.toString());
            List<IpTester> ipList = loadIpList(file.toString());

            AtomicInteger counter = new AtomicInteger(0);
            readBlocked((ips, hosts, pages, who, act, date) -> {
                if (testIps(ipList, ips) || testHosts(hostList, hosts)) {
                    if (isTriggerMode) {
                        counter.incrementAndGet();
                    } else
                        report(ips, hosts, pages, who, act, date);
                }
            });

            if (isTriggerMode)
                System.out.print(counter.get());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class IpTester {
        private String ip;
        private SubnetUtils.SubnetInfo subnet = null;

        IpTester(String ip) {
            this.ip = ip;
            if (ip.contains("/")) {
                subnet = new SubnetUtils(ip).getInfo();
            }
        }

        boolean test(String ip) {
            if (subnet != null) {
                return subnet.isInRange(ip);
            } else {
                return ip.equals(this.ip);
            }
        }
    }

    private interface Handler {
        void handle(String[] ips, String[] hosts, String[] pages, String who, String act, String date);
    }
}
