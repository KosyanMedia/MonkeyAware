package com.jetradar.monkeyaware;

import org.xbill.DNS.*;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NsLookup {
//    private static final String DNS_LIST = "http://public-dns.info/nameserver/ru.txt";
    private static final String[] DNS = new String[]{"77.88.8.8", "77.88.8.1", "8.8.8.8", "8.8.4.4"}; // yandex & google
    private static final List<ExtendedResolver> dnsResolvers;
    static {
        try {
            dnsResolvers = buildDnsList();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private static List<ExtendedResolver> buildDnsList() throws IOException {
        List<String> dnsList = new ArrayList<>(Arrays.asList(DNS));

//        HttpURLConnection conn = (HttpURLConnection) new URL(DNS_LIST).openConnection();
//        conn.setRequestMethod("GET");
//        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(conn.getInputStream()));
//        String line = lnr.readLine();
//        while (line != null) {
//            if (!dnsList.contains(line))
//                dnsList.add(line);
//            line = lnr.readLine();
//        }
//        lnr.close();

        List<ExtendedResolver> result = new ArrayList<>();
        for (String dns : dnsList)
            try {
                result.add(new ExtendedResolver(new String[]{dns}));
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            }
        return result;
    }

    public static List<String> getHostsList(String host) throws TextParseException, UnknownHostException, NullPointerException {
        List<String> result = new ArrayList<>();

        Lookup lookup = new Lookup(host, Type.ANY, DClass.IN);
        for(ExtendedResolver dnsResolver : dnsResolvers){
            lookup.setResolver(dnsResolver);
            lookup.setCache(new Cache(DClass.IN));
            Record[] records = lookup.run();
            for (Record record : records) {
                if (record.getType() == Type.A) {
                    String ip = ((ARecord) record).getAddress().getHostAddress();
                    if (!result.contains(ip))
                        result.add(ip);
                }
            }
        }

        return result;
    }

    public static void main(String... args) {
        try {
            List<String> ipList = getHostsList("rollbar.com");

            for (String ip : ipList)
                System.out.println(ip);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
