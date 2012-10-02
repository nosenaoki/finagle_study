package com.ccc.tparty.proto;

import jp.co.imj.proto.StreamServer;

public class Main {
    public static void main(String[] args) {
//	ChatServer chatServer = new ChatServer();
//	chatServer.start();
	StreamServer streamServer = new StreamServer();
	streamServer.start();
    }
}
