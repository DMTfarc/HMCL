/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.logging.appender;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jackhuang.hellominecraft.logging.LogEvent;
import org.jackhuang.hellominecraft.logging.LoggingException;
import org.jackhuang.hellominecraft.logging.layout.ILayout;

/**
 *
 * @author hyh
 */
public abstract class OutputStreamAppender extends AbstractAppender {

    protected final OutputStream stream;
    protected final boolean immediateFlush;
    private final Lock readLock = new ReentrantReadWriteLock().readLock();

    public OutputStreamAppender(String name, ILayout<? extends Serializable> layout, boolean ignoreExceptions, OutputStream stream, boolean immediateFlush) {
	super(name, layout, ignoreExceptions);

	this.immediateFlush = immediateFlush;
	this.stream = stream;
    }

    @Override
    public void append(LogEvent event) {
	this.readLock.lock();
	try {
	    byte[] bytes = getLayout().toByteArray(event);
	    if (bytes.length > 0) {
		stream.write(bytes);
	    }
	    if(event.thrown != null)
		event.thrown.printStackTrace(new PrintStream(stream));
	} catch (IOException ex) {
	    System.err.println("Unable to write to stream for appender: " + getName());
	    throw new LoggingException(ex);
	} finally {
	    this.readLock.unlock();
	}
    }
}
