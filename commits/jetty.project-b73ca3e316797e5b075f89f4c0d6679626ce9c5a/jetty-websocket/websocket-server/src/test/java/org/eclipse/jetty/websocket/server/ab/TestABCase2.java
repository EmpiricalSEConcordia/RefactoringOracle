package org.eclipse.jetty.websocket.server.ab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.protocol.CloseInfo;
import org.eclipse.jetty.websocket.protocol.OpCode;
import org.eclipse.jetty.websocket.protocol.WebSocketFrame;
import org.junit.Test;

public class TestABCase2 extends AbstractABCase
{
    /**
     * Ping without payload
     */
    @Test
    public void testCase2_1() throws Exception
    {
        WebSocketFrame send = WebSocketFrame.ping();

        WebSocketFrame expect = WebSocketFrame.pong();

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * 10 pings
     */
    @Test
    public void testCase2_10() throws Exception
    {
        // send 10 pings each with unique payload
        // send close
        // expect 10 pongs with our unique payload
        // expect close

        int pingCount = 10;

        List<WebSocketFrame> send = new ArrayList<>();
        List<WebSocketFrame> expect = new ArrayList<>();

        for (int i = 0; i < pingCount; i++)
        {
            String payload = String.format("ping-%d[%X]",i,i);
            send.add(WebSocketFrame.ping().setPayload(payload));
            expect.add(WebSocketFrame.pong().setPayload(payload));
        }
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * 10 pings, sent slowly
     */
    @Test
    public void testCase2_11() throws Exception
    {
        // send 10 pings (slowly) each with unique payload
        // send close
        // expect 10 pongs with OUR payload
        // expect close

        int pingCount = 10;

        List<WebSocketFrame> send = new ArrayList<>();
        List<WebSocketFrame> expect = new ArrayList<>();

        for (int i = 0; i < pingCount; i++)
        {
            String payload = String.format("ping-%d[%X]",i,i);
            send.add(WebSocketFrame.ping().setPayload(payload));
            expect.add(WebSocketFrame.pong().setPayload(payload));
        }
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.SLOW);
            fuzzer.setSlowSendSegmentSize(5);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Ping with small text payload
     */
    @Test
    public void testCase2_2() throws Exception
    {
        byte payload[] = StringUtil.getUtf8Bytes("Hello world");

        List<WebSocketFrame> send = new ArrayList<>();
        send.add(WebSocketFrame.ping().setPayload(payload));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(WebSocketFrame.pong().setPayload(payload));
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Ping with small binary (non-utf8) payload
     */
    @Test
    public void testCase2_3() throws Exception
    {
        byte payload[] = new byte[]
        { 0x00, (byte)0xFF, (byte)0xFE, (byte)0xFD, (byte)0xFC, (byte)0xFB, 0x00, (byte)0xFF };

        List<WebSocketFrame> send = new ArrayList<>();
        send.add(WebSocketFrame.ping().setPayload(payload));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(WebSocketFrame.pong().setPayload(payload));
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Ping with 125 byte binary payload
     */
    @Test
    public void testCase2_4() throws Exception
    {
        byte payload[] = new byte[125];
        Arrays.fill(payload,(byte)0xFE);

        List<WebSocketFrame> send = new ArrayList<>();
        send.add(WebSocketFrame.ping().setPayload(payload));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(WebSocketFrame.pong().setPayload(payload));
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Ping with 126 byte binary payload
     */
    @Test
    public void testCase2_5() throws Exception
    {
        byte payload[] = new byte[126]; // intentionally too big
        Arrays.fill(payload,(byte)0xFE);

        List<WebSocketFrame> send = new ArrayList<>();
        // trick websocket frame into making extra large payload for ping
        send.add(WebSocketFrame.binary(payload).setOpCode(OpCode.PING));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.PROTOCOL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Ping with 125 byte binary payload (slow send)
     */
    @Test
    public void testCase2_6() throws Exception
    {
        byte payload[] = new byte[125];
        Arrays.fill(payload,(byte)0xFE);

        List<WebSocketFrame> send = new ArrayList<>();
        send.add(WebSocketFrame.ping().setPayload(payload));
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(WebSocketFrame.pong().setPayload(payload));
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.SLOW);
            fuzzer.setSlowSendSegmentSize(1);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Unsolicited pong frame without payload
     */
    @Test
    public void testCase2_7() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(WebSocketFrame.pong()); // unsolicited pong
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Unsolicited pong frame with basic payload
     */
    @Test
    public void testCase2_8() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(WebSocketFrame.pong().setPayload("unsolicited")); // unsolicited pong
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }

    /**
     * Unsolicited pong frame, then ping with basic payload
     */
    @Test
    public void testCase2_9() throws Exception
    {
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(WebSocketFrame.pong().setPayload("unsolicited")); // unsolicited pong
        send.add(WebSocketFrame.ping().setPayload("our ping")); // our ping
        send.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        List<WebSocketFrame> expect = new ArrayList<>();
        expect.add(WebSocketFrame.pong().setPayload("our ping")); // our pong
        expect.add(new CloseInfo(StatusCode.NORMAL).asFrame());

        Fuzzer fuzzer = new Fuzzer(this);
        try
        {
            fuzzer.connect();
            fuzzer.setSendMode(Fuzzer.SendMode.BULK);
            fuzzer.send(send);
            fuzzer.expect(expect);
        }
        finally
        {
            fuzzer.close();
        }
    }
}
