/*
 * Copyright 2019-2021 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * If you use this software for scientific research then please cite the following publication(s):
 *
 * M. Hoffer, C. Poliwoda, & G. Wittum. (2013). Visual reflection library:
 * a framework for declarative GUI programming on the Java platform.
 * Computing and Visualization in Science, 2013, 16(4),
 * 181–192. http://doi.org/10.1007/s00791-014-0230-y
 */
package eu.mihosoft.streamutils;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

/**
 * StreamUtils provides a collection of stream related utility methods. This includes methods for reading a
 * stream until a specified predicate matches.
 */
public final class StreamUtils {

    private StreamUtils() {
        throw new AssertionError("Don't instantiate me!");
    }

    /**
     * Reads a string from the specified input stream until the specified predicate matches. The purpose of this
     * method is to prevent unlimited lookahead parsers such as ANTLR from blocking an input stream indefinitely.
     * The data is read byte by byte to prevent potential blocking and guarantee correct matching of the read data.
     *
     * @param matches predicate to match end of expected data
     * @param inputStream input stream used for reading data
     *
     * @return string data read from the stream
     * @throws IOException if reading was not successful
     */
    public static String readStringUntilMatches(
            Predicate<String> matches,
            InputStream inputStream
    ) throws IOException {
        return readStringUntilMatches(matches, inputStream, 1024, StandardCharsets.UTF_8);
    }

    /**
     * Reads a string from the specified input stream until the specified predicate matches. The purpose of this
     * method is to prevent unlimited lookahead parsers such as ANTLR from blocking an input stream indefinitely.
     * The data is read byte by byte to prevent potential blocking and guarantee correct matching of the read data.
     *
     * @param matches predicate to match end of expected data
     * @param inputStream input stream used for reading data
     * @param bufferSize internal buffer size (sliding window)
     * @param charset charset to use for reading string from the specified stream
     *
     * @return string data read from the stream
     * @throws IOException if reading was not successful
     */
    public static String readStringUntilMatches(
            Predicate<String> matches,
            InputStream inputStream,
            int bufferSize,
            Charset charset) throws IOException {
        // this method is based on VRL/VMF tools

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] readData = new byte[1];
        int i = 0;

        while(true) {

            // try to read a single byte
            int readBytes = inputStream.read(readData,0,1);

            // if no data could be read throw an exception
            if(readBytes < 1) throw new IOException("Cannot read input until requested delimiter. Buffer: " + bos.toString(charset));
            // collect the data for string generation
            bos.write(readData);

            // convert the data to string
            String buffer = bos.toString(charset);
            // strip leading characters and reduce buffer string to specified buffer size
            // -> reduces performance issues when matching delimiters/patterns
            if(i >= bufferSize) {
                buffer = buffer.substring(buffer.length()-bufferSize);
            }

            // check whether delimiter can be found
            if(matches.test(buffer)) {
                return bos.toString(charset);
            }

            // make sure buffer string stays within buffer limit
            if(i < bufferSize) {
                i++;
            }

        }
    }

    // used for debugging
    private static String showCRLF(String s) {
        return s.replace("\r\n", "<CRLF>").replace("\r", "<CR>").replace("\n", "<LF>");
    }


    /**
     * A stream that wraps around a specified input stream and captures every byte that is read from the source stream.
     */
    public static final class ReadingInputStream extends FilterInputStream {

        private static final int EOF = -1;
        private final ByteArrayOutputStream bos;

        /**
         * Constructs a new ReadingInputStream.
         *
         * @param src the InputStream to read from
         */
        public ReadingInputStream(final InputStream src) {
            super(src);
            this.bos = new ByteArrayOutputStream();
        }

        /**
         * Returns the bytes that have been read by this stream after instantiation/clearBuffer() calls.
         * @return the bytes that have been read by this stream
         */
        public byte[] getReadBytes() {
            return this.bos.toByteArray();
        }

        /**
         * Clears the internal byte buffer.
         */
        public void clearBuffer() {
            this.bos.reset();
        }

        @Override
        public int readNBytes(byte[] b, int off, int len) throws IOException {
            final int n = in.readNBytes(b, off, len);
            processRead(b, off, n);

            return n;
        }

        @Override
        public byte[] readNBytes(int len) throws IOException {
            byte[] b = new byte[len];
            final int n = in.readNBytes(b, 0, len);
            processRead(b, 0, n);

            return b;
        }

        /**
         * Invokes the source streams <code>read()</code> method. This stream reads the returned content into
         * the internal buffer to be consumed and cleared by the consumer of this API.
         * @return the byte read or -1 if the end of stream
         * @throws IOException if an I/O error occurs
         */
        @Override
        public int read() throws IOException {
            final int b = in.read();
            processRead(b, b != EOF ? 1 : EOF);
            return b;
        }

        /**
         * Invokes the sources's <code>read(byte[])</code> method.
         * @param b the buffer to read the bytes into
         * @return the number of bytes read or EOF if the end of stream
         * @throws IOException if an I/O error occurs
         */
        @Override
        public int read(final byte[] b) throws IOException {
            final int n = in.read(b);
            processRead(b, 0, n);
            return n;
        }

        /**
         * Invokes the delegate's <code>read(byte[], int, int)</code> method.
         * @param b the buffer to read the bytes into
         * @param off The start offset
         * @param len The number of bytes to read
         * @return the number of bytes read or -1 if the end of stream
         * @throws IOException if an I/O error occurs
         */
        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final int n = in.read(b, off, len);
            processRead(b, off, n);
            return n;
        }

        /**
         * Invokes the source's <code>skip(long)</code> method.
         * @param ln the number of bytes to skip
         * @return the actual number of bytes skipped
         * @throws IOException if an I/O error occurs
         */
        @Override
        public long skip(final long ln) throws IOException {
            return in.skip(ln);
        }

        /**
         * Invokes the source's <code>available()</code> method.
         * @return the number of available bytes
         * @throws IOException if an I/O error occurs
         */
        @Override
        public int available() throws IOException {
            return super.available();
        }

        /**
         * Invokes the sources's <code>close()</code> method and closes the associated buffer.
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            in.close();
        }

        /**
         * MArks are not supported by this stream.
         * @param readlimit read ahead limit
         */
        @Override
        public synchronized void mark(final int readlimit) {
            throw new UnsupportedOperationException("Marks & reset not supported by this stream");
        }

        /**
         * This stream does not support resets.
         * @throws IOException if this methos is called
         */
        @Override
        public synchronized void reset() throws IOException {
            throw new IOException("Marks & reset not supported by this stream");
        }

        /**
         * This stream does currently not support marks.
         * @return {@code false}
         */
        @Override
        public boolean markSupported() {
            return false;
        }

        /**
         * Invoked by the read methods after the sources call has returned. Stores the read bytes into the internal buffer.
         *
         * @param b byte that has been read
         * @param n number of bytes read, or -1 if the end of stream was reached
         * @throws IOException if the post-processing fails
         */
        private void processRead(final int b, final int n) throws IOException {
            if(n > 0) bos.write(b);
        }

        /**
         * Invoked by the read methods after the sources call has returned
         *
         * @param b bytes that have been read
         * @throws IOException if the post-processing fails
         */
        private void processRead(final byte[] b) throws IOException {
            int len = b==null?0:b.length;
            if(len > 0) bos.write(b, 0, len);
        }

        /**
         * Invoked by the read methods after the sources call has returned
         *
         * @param b bytes that have been read
         * @param offset offset in the specified byte array
         * @param len number of bytes read, or -1 if the end of stream was reached
         * @throws IOException if the post-processing fails
         */
        private void processRead(final byte[] b, final int offset, final int len) throws IOException {
            if(len > 0) {
                bos.write(b, offset, len);
            }
        }
    }

}