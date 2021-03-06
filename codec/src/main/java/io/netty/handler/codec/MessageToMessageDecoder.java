/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.ReferenceCounted;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MessageList;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * {@link ChannelInboundHandlerAdapter} which decodes from one message to an other message.
 *
 *
 * For example here is an implementation which decodes a {@link String} to an {@link Integer} which represent
 * the length of the {@link String}.
 *
 * <pre>
 *     public class StringToIntegerDecoder extends
 *             {@link MessageToMessageDecoder}&lt;{@link String}&gt; {
 *
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link String} message,
 *                 {@link MessageList} out) throws {@link Exception} {
 *             out.add(message.length());
 *         }
 *     }
 * </pre>
 *
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed through if they
 * are of type {@link ReferenceCounted}. This is needed as the {@link MessageToMessageDecoder} will call
 * {@link ReferenceCounted#release()} on decoded messages.
 *
 */
public abstract class MessageToMessageDecoder<I> extends ChannelInboundHandlerAdapter {

    private final TypeParameterMatcher matcher;

    protected MessageToMessageDecoder() {
        matcher = TypeParameterMatcher.find(this, MessageToMessageDecoder.class, "I");
    }

    protected MessageToMessageDecoder(Class<? extends I> inboundMessageType) {
        matcher = TypeParameterMatcher.get(inboundMessageType);
    }

    public boolean acceptInboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        MessageList<Object> out = MessageList.newInstance();
        try {
            int size = msgs.size();
            for (int i = 0; i < size; i ++) {
                // handler was removed in the loop so now copy over all remaining messages
                if (ctx.isRemoved()) {
                    out.add(msgs, i, size - i);
                    return;
                }

                Object m = msgs.get(i);
                if (acceptInboundMessage(m)) {
                    @SuppressWarnings("unchecked")
                    I cast = (I) m;
                    try {
                        decode(ctx, cast, out);
                    } finally {
                        ByteBufUtil.release(cast);
                    }
                } else {
                    out.add(m);
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            msgs.recycle();
            ctx.fireMessageReceived(out);
        }
    }

    /**
     * Decode from one message to an other. This method will be called till either the {@link MessageList} has
     * nothing left or till this method returns {@code null}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageDecoder} belongs to
     * @param msg           the message to decode to an other one
     * @param out           the {@link MessageList} to which decoded messages should be added
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void decode(ChannelHandlerContext ctx, I msg, MessageList<Object> out) throws Exception;
}
