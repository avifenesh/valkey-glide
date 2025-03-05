module glide.api {
    requires com.google.protobuf;
    requires io.netty.codec;
    requires io.netty.common;
    requires io.netty.transport;
    requires io.netty.transport.classes.epoll;
    requires io.netty.transport.classes.kqueue;
    requires io.netty.transport.unix.common;
    requires lombok;
    requires org.apache.commons.lang3;
}
