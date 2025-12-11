package com.qg.engine;

public class App {
    public static void main(String[] args) throws Exception {
        System.out.println("[ENGINE] Starting QG Java Engine...");

        CommunicationService service = new CommunicationService();
        service.start();
    }
}
