package com.jetradar.monkeyaware;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.net.util.SubnetUtils;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.lang.StringEscapeUtils.unescapeJava;

public class MonkeyAware {
    private static final String BLOCKED_HOSTS_URL = "https://raw.githubusercontent.com/zapret-info/z-i/master/dump.csv";
    private static final String QS = "\"";

    private static void readBlocked(Handler handler) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(BLOCKED_HOSTS_URL).openConnection();
        conn.setRequestMethod("GET");

        Iterable<CSVRecord> records = CSVFormat.RFC4180.withDelimiter(';')
                .parse(new InputStreamReader(conn.getInputStream(),
                Charset.forName("windows-1251")));
        for(CSVRecord record : records){
            if (record.size() == 6) {
                handler.handle(toArray(record.get(0)),
                        toArray(record.get(1)),
                        toArray(record.get(2)),
                        record.get(3),
                        record.get(4),
                        record.get(5));
            }
        }
    }

    private static String[] toArray(String list) {
        String[] tmp = list.split(" | ");
        List<String> split = new ArrayList<>();
        for (String i : tmp)
            if (!i.equals("|"))
                split.add(i);
        return split.toArray(new String[split.size()]);
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
        Iterator<CSVRecord> records = CSVFormat.RFC4180.parse(in).iterator();
        records.next(); // skip header
        while (records.hasNext()){
            CSVRecord record = records.next();
            if (column < record.size() && !record.get(column).trim().isEmpty()) {
                result.add(record.get(column));
            }
        }
        return result;
    }

    public static void main(String... args) {
        try {
            if (args.length < 2) {
                System.out.println("use com.jetradar.monkeyaware.MonkeyAware mode=[trigger|report] path/to/file.csv");
                System.exit(1);
            }
            boolean isTriggerMode = args[0].equals("mode=trigger");

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
