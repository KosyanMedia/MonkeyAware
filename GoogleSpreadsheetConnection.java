package com.jetradar.monkeyaware;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.api.services.sheets.v4.Sheets;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GoogleSpreadsheetConnection {
    private static final String APPLICATION_NAME = "MonkeyAwareApp";

    /**
     * Directory to store user credentials for this application.
     */
    private static final java.io.File DATA_STORE_DIR =
            new java.io.File(System.getProperty("user.home"), ".credentials/MonkeyAware");


    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static HttpTransport HTTP_TRANSPORT;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException
     */
    private static Credential authorize() throws IOException {
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT,
                        JSON_FACTORY,
                        GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(
                                GoogleSpreadsheetConnection.class.getResourceAsStream("/client-key.json")
                        )),
                        Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY)
                )
                        .setDataStoreFactory(new FileDataStoreFactory(DATA_STORE_DIR))
                        .setApprovalPrompt("auto")
                        .setAccessType("offline")
                        .build();
        Credential credential = flow.loadCredential("user");
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    /**
     * Build and return an authorized Sheets API client service.
     *
     * @return an authorized Sheets API client service
     * @throws IOException
     */
    private static Sheets getSheetsService() throws IOException {
        Credential credential = authorize();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static List<List<String>> getTable(String spreadsheetId, String sheetName) throws IOException {
        Sheets service = getSheetsService();
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!A2:E")
                .execute();
        List<List<Object>> values = response.getValues();

        List<List<String>> result = new ArrayList<>();
        if (values != null && values.size() > 0) for (List row : values) {
            List<String> r = new ArrayList<>(5);
            for (int i = 0; i < 5; i++)
                if (i >= row.size())
                    r.add("");
                else
                    r.add(row.get(i).toString());
            result.add(r);
        }
        return result;
    }

    private static String escape(String in) {
        StringBuilder out = new StringBuilder();
        for (char c : in.toCharArray()) {
            switch (c) {
                case '\\':
                    out.append('\\');
                    out.append(c);
                case '"':
                    out.append('\\');
                    out.append(c);
                    break;
                case '/':
                    out.append('\\');
                    out.append(c);
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                default:
                    if (0x20 <= c && c <= 0x7D)
                        out.append(c);
                    else {
                        String t = "000" + Integer.toHexString(c);
                        out.append("\\u").append(t.substring(t.length() - 4));
                    }
            }
        }
        return out.toString();
    }

    public static void main(String[] args) throws IOException {
        // https://docs.google.com/spreadsheets/d/16d-pm9F-N4J0Ctih7WLO9i1D0s-aBrEPvNtDMioMuQ8/edit#gid=0
        List<List<String>> table = getTable("16d-pm9F-N4J0Ctih7WLO9i1D0s-aBrEPvNtDMioMuQ8", "Service list");

        PrintWriter pw = new PrintWriter(new FileOutputStream("./table.csv", false));
        for (List<String> row : table) {
            for (int c = 0; c < row.size(); c++) {
                String cell = escape(row.get(c));
                pw.print(cell);
                if (c < row.size() - 1)
                    pw.print(";");
                else
                    pw.println();
            }
        }
        pw.close();
    }
}