/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.component.fileoperation;

import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.jetel.component.fileoperation.FileManager.WritableContentProvider;
import org.jetel.component.fileoperation.SimpleParameters.CopyParameters;
import org.jetel.component.fileoperation.SimpleParameters.CreateParameters;
import org.jetel.component.fileoperation.SimpleParameters.DeleteParameters;
import org.jetel.component.fileoperation.SimpleParameters.ListParameters;
import org.jetel.component.fileoperation.SimpleParameters.MoveParameters;
import org.jetel.component.fileoperation.result.CopyResult;
import org.jetel.component.fileoperation.result.CreateResult;
import org.jetel.component.fileoperation.result.DeleteResult;
import org.jetel.component.fileoperation.result.InfoResult;
import org.jetel.component.fileoperation.result.ListResult;
import org.jetel.component.fileoperation.result.MoveResult;
import org.jetel.component.fileoperation.result.ResolveResult;
import org.jetel.test.CloverTestCase;
import org.jetel.util.file.FileUtils;

public abstract class OperationHandlerTestTemplate extends CloverTestCase {
	
	protected static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	
	protected static final Charset charset = Charset.forName("UTF-8");

	protected static final int IO_BUFFER_SIZE = 4 * 1024; 
	
	protected static boolean VERBOSE = true;

	protected FileManager manager = null;
	protected URI baseUri = null;
	
	protected abstract IOperationHandler createOperationHandler();
	
	protected abstract URI createBaseURI();
	
	protected void setBaseURI() {
		baseUri = createBaseURI();
		if (baseUri == null) {
			throw new IllegalStateException("Base URI is null");
		}
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		initEngine();
		manager = FileManager.getInstance();
		manager.clear();
		IOperationHandler handler = createOperationHandler();
		manager.registerHandler(VERBOSE ? new ObservableHandler(handler) : handler);
		setBaseURI();
	}
	
	@Override
	protected void tearDown() throws Exception {
		Thread.interrupted(); // reset the interrupted flag of the current thread
		super.tearDown();
		manager = null;
		baseUri = null;
	}

	public abstract void testGetPriority();

	public abstract void testCanPerform();
	
	protected void copy(InputStream input, OutputStream output) throws IOException {
		byte[] b = new byte[IO_BUFFER_SIZE];  
		int read;  
		while ((read = input.read(b)) != -1) {
			output.write(b, 0, read);  
		}  
	}
	
	protected void prepareData(CloverURI uri, String data) throws Exception {
		assumeTrue(manager.create(uri, new CreateParameters().setMakeParents(true)).success());
		assumeTrue(write(manager.getOutput(uri).channel(), data));
	}

	public void testCopy() throws Exception {
		Map<String, String> texts = new HashMap<String, String>();
		texts.put("srcdir/file.tmp", "Žluťoučký kůň úpěl ďábelské ódy");
		texts.put("srcdir/f.tmp", "Tak se z lesa ozývá");
		texts.put("srcdir/found.tmp", "V lese padají šišky");
		texts.put("srcdir/subdir/found.tmp", "V lese padají šišky");
		texts.put("root.tmp", "Root file");
		prepareData(texts);
		assumeTrue(manager.isDirectory(relativeURI("srcdir")));
		assumeTrue(manager.isDirectory(relativeURI("srcdir/subdir")));
		assumeTrue(manager.create(relativeURI("a/d/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("b/d/d/d/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("c/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("d/d/d/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("e/d/d/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("g/a/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("h/a/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("p.tmp")).success());
		assumeTrue(manager.create(relativeURI("q.tmp")).success());
		assumeTrue(manager.create(relativeURI("r/")).success());
		assumeTrue(manager.create(relativeURI("s/dir/;s/file.tmp"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("t/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("u/a.tmp;u/b.tmp"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("w.tmp")).success());
		assumeTrue(manager.create(relativeURI("samefile/f.tmp;samefile/dir/"), new CreateParameters().setMakeParents(true)).success());
		
		CloverURI source;
		CloverURI target;
		CopyResult result;
		
		source = relativeURI("srcdir/file.tmp");
		target = relativeURI("targetdir/copy.tmp");
		assertFalse(manager.copy(source, target).success());
		assumeTrue(manager.create(relativeURI("targetdir/")).success());
		assertTrue(manager.copy(source, target).success());
		
		source = relativeURI("srcdir");
		target = relativeURI("srcdir_copy");
		result = manager.copy(source, target);
		assertTrue(result.success());
		assertEquals(0, result.totalCount());
		assertTrue(manager.copy(source, target, new CopyParameters().setRecursive(true)).success());
		for (String path: texts.keySet()) {
			if (path.startsWith(source.toString())) {
				assertEquals(texts.get(path), read(manager.getInput(relativeURI(path.replaceFirst(source.toString(), target.toString()))).channel()));
				assertTrue(manager.exists(relativeURI(path)));
			}
		}
		
		source = relativeURI("a/d");
		target = relativeURI("b/d");
		assertTrue(manager.copy(source, target, new CopyParameters().setRecursive(true)).success());
		assertTrue(manager.exists(relativeURI("b/d/d/f.tmp")));
		assertTrue(manager.exists(relativeURI("a/d/f.tmp")));

		source = relativeURI("d");
		target = relativeURI("c");
		assertTrue(manager.copy(source, target, new CopyParameters().setRecursive(true)).success());
		assertTrue(manager.exists(relativeURI("c/d/d/d/f.tmp")));
		
		target = relativeURI("e");
		assertTrue(manager.copy(source, target, new CopyParameters().setRecursive(true)).success());
		assertTrue(manager.exists(relativeURI("e/d/d/d/f.tmp")));

		target = relativeURI("e/d");
		assertTrue(manager.copy(source, target, new CopyParameters().setRecursive(true)).success());
		assertTrue(manager.exists(relativeURI("e/d/d/d/d/f.tmp")));

		source = relativeURI("a");
		target = relativeURI("g");
		assertTrue(manager.copy(source, target, new CopyParameters().setRecursive(true)).success());
		assertTrue(manager.exists(relativeURI("g/a/d/f.tmp")));

		target = relativeURI("h/a");
		assertTrue(manager.copy(source, target, new CopyParameters().setRecursive(true)).success());
		assertTrue(manager.exists(relativeURI("h/a/a/d/f.tmp")));

		target = relativeURI("i");
		assertTrue(manager.copy(source, target, new CopyParameters().setRecursive(true)).success());
		assertTrue(manager.exists(relativeURI("i/d/f.tmp")));

		source = relativeURI("p.tmp");
		target = relativeURI("root.tmp");
		assertTrue(manager.copy(source, target, new CopyParameters().setNoOverwrite()).success());
		assertEquals("File was overwritten in no-overwrite mode", texts.get(target.toString()), read(manager.getInput(target).channel()));

		{
			String originalContent = "Original content";
			source = relativeURI("olderFile.tmp");
			target = relativeURI("newerFile.tmp");
			assumeTrue(manager.create(source).success()); // older file
			prepareData(target, "Original content"); // newer file
			assertTrue("Update mode returned an error", manager.copy(source, target, new CopyParameters().setUpdate()).success());
			assertEquals("File was overwritten with older file in update mode", originalContent, read(manager.getInput(target).channel()));
		}
		
		source = relativeURI("q.tmp");
		target = relativeURI("r");
		result = manager.copy(source, target);
		assertTrue(result.success());
		assertTrue(result.getResult(0).getPath().endsWith("r/q.tmp"));
		
		source = relativeURI("s/*");
		target = relativeURI("t");
		result = manager.copy(source, target);
		assertTrue(result.success());
		assertEquals(1, result.totalCount());
		assertEquals(1, result.successCount());
		assertEquals(0, result.errorCount());

		source = relativeURI("u/*");
		target = relativeURI("v");
		result = manager.copy(source, target);
		assertFalse(result.success());
		
		source = relativeURI("w.tmp");
		target = source;
		result = manager.copy(source, target);
		assertFalse(result.success());
		assertTrue(manager.exists(source));
		
		source = relativeURI("samefile/f.tmp");
		target = relativeURI("samefile");
		result = manager.copy(source, target);
		assertFalse(result.success());
		assertTrue(manager.exists(source));

		source = relativeURI("samefile/dir");
		target = relativeURI("samefile");
		result = manager.copy(source, target, new CopyParameters().setRecursive(true));
		assertFalse(result.success());
		assertTrue(manager.exists(source));

		source = relativeURI("samefile/f.tmp");
		target = relativeURI("samefile/./f.tmp");
		result = manager.copy(source, target);
		assertFalse(result.success());
		assertTrue(manager.exists(source));
		
		source = relativeURI("samefile/f.tmp");
		target = relativeURI("samefile/../samefile/f.tmp");
		result = manager.copy(source, target);
		assertFalse(result.success());
		assertTrue(manager.exists(source));
	}
	
	public void testSpecialCharacters() throws Exception {
		CloverURI uri;
		
		uri = relativeURI("moje složka/");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(manager.create(uri).success());
		assertTrue(String.format("%s is not a directory", uri), manager.isDirectory(uri));

		uri = relativeURI("druhá složka/žluťoučký souboreček.tmp");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(manager.create(uri, new CreateParameters().setMakeParents(true)).success());
		assertTrue(String.format("%s is not a file", uri), manager.isFile(uri));

		uri = relativeURI("123čřž !@$&().tmp");
		// #%^{[;:|<> - illegal FIXME
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(manager.create(uri).success());
		assertTrue(String.format("%s is not a file", uri), manager.isFile(uri));

		assumeTrue(manager.create(relativeURI("moje složka/žluťoučký souboreček.tmp"), new CreateParameters().setMakeParents(true)).success());

		CloverURI source;
		CloverURI target;

		{
			source = relativeURI("moje složka/žluťoučký souboreček.tmp");
			target = relativeURI("moje složka/žluťoučká kopie");
			CopyResult result = manager.copy(source, target);
			assertTrue(result.success());
			assertTrue(manager.exists(target));
		}
	}

	public void testInfo() throws Exception {
		assumeTrue(manager.create(relativeURI("foo/bar"), new CreateParameters().setMakeParents(true)).success());
		
		CloverURI uri;
		InfoResult info;
		
		uri = relativeURI("foo");
		System.out.println(uri.getAbsoluteURI());
		info = manager.info(uri);
		assertTrue(String.format("%s is not a directory", uri), info.isDirectory());
		
		uri = relativeURI("foo/bar");
		System.out.println(uri.getAbsoluteURI());
		info = manager.info(uri);
		assertTrue(String.format("%s is not a file", uri), info.isFile());
		
	}
	
	protected void prepareData(Map<String, String> texts) throws Exception {
		for (Map.Entry<String, String> e: texts.entrySet()) {
			prepareData(relativeURI(e.getKey()), e.getValue());
		}
	}

	public void testMove() throws Exception {
		Map<String, String> texts = new HashMap<String, String>();
		texts.put("srcdir/file.tmp", "Žluťoučký kůň úpěl ďábelské ódy");
		texts.put("srcdir/f.tmp", "Tak se z lesa ozývá");
		texts.put("srcdir/found.tmp", "V lese padají šišky");
		texts.put("srcdir/subdir/found.tmp", "V lese padají šišky");
		texts.put("root.tmp", "Root file");
		prepareData(texts);
		assumeTrue(manager.isDirectory(relativeURI("srcdir")));
		assumeTrue(manager.isDirectory(relativeURI("srcdir/subdir")));
		assumeTrue(manager.create(relativeURI("a/d/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("b/d/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("c/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("d/d/d/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("e/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("g/a/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("h/a/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("o/d/"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("p.tmp")).success());
		assumeTrue(manager.create(relativeURI("q.tmp")).success());
		assumeTrue(manager.create(relativeURI("r/")).success());
		assumeTrue(manager.create(relativeURI("s/a.tmp;s/b.tmp"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("u.tmp")).success());
		assumeTrue(manager.create(relativeURI("samefile/f.tmp;samefile/dir/"), new CreateParameters().setMakeParents(true)).success());
		
		CloverURI source;
		CloverURI target;
		
		source = relativeURI("srcdir/file.tmp");
		target = relativeURI("targetdir/copy.tmp");
		assertFalse(manager.move(source, target).success());
		assumeTrue(manager.create(relativeURI("targetdir/")).success());
		assertTrue(manager.move(source, target).success());
		prepareData(texts);
		
		source = relativeURI("srcdir");
		target = relativeURI("srcdir_copy");
		assertTrue(manager.move(source, target).success());
		for (String path: texts.keySet()) {
			if (path.startsWith(source.toString())) {
				assertEquals(texts.get(path), read(manager.getInput(relativeURI(path.replaceFirst(source.toString(), target.toString()))).channel()));
				assertFalse(manager.exists(relativeURI(path)));
			}
		}

		source = relativeURI("a/d");
		target = relativeURI("b/d");
		MoveResult result = manager.move(source, target); 
		assertTrue(result.success());
		assertTrue(manager.exists(relativeURI("b/d/d/f.tmp")));
		assertFalse(manager.exists(source));

		source = relativeURI("d");
		target = relativeURI("c");
		assertTrue(manager.move(source, target).success());
		assertTrue(manager.exists(relativeURI("c/d/d/d/f.tmp")));
		assertFalse(manager.exists(source));
		
		assumeTrue(manager.create(relativeURI("d/d/d/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		target = relativeURI("e");
		assertTrue(manager.move(source, target).success());
		assertTrue(manager.exists(relativeURI("e/d/d/d/f.tmp")));
		assertFalse(manager.exists(source));

		assumeTrue(manager.create(relativeURI("a/d/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		source = relativeURI("a");
		target = relativeURI("g");
		assertTrue(manager.move(source, target).success());
		assertTrue(manager.exists(relativeURI("g/a/d/f.tmp")));
		assertFalse(manager.exists(source));

		assumeTrue(manager.create(relativeURI("a/d/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		target = relativeURI("h/a");
		assertTrue(manager.move(source, target).success());
		assertTrue(manager.exists(relativeURI("h/a/a/d/f.tmp")));
		assertFalse(manager.exists(source));

		assumeTrue(manager.create(relativeURI("a/d/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		target = relativeURI("i");
		assertTrue(manager.move(source, target).success());
		assertTrue(manager.exists(relativeURI("i/d/f.tmp")));
		assertFalse(manager.exists(source));

		source = relativeURI("j.tmp");
		target = relativeURI("k.tmp");
		assumeTrue(manager.create(source, new CreateParameters().setMakeParents(true)).success());
		assertTrue(manager.move(source, target).success());
		assertTrue(manager.exists(target));
		assertFalse(manager.exists(source));
		assumeTrue(manager.create(source, new CreateParameters().setMakeParents(true)).success());
		assertTrue(manager.move(source, target).success());
		assertTrue(manager.exists(target));
		assertFalse(manager.exists(source));
		
		assumeTrue(manager.create(relativeURI("l/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("m/l/g.tmp"), new CreateParameters().setMakeParents(true)).success());
		assumeTrue(manager.create(relativeURI("n/l/"), new CreateParameters().setMakeParents(true)).success());
		source = relativeURI("l");
		target = relativeURI("m");
		assertFalse(manager.move(source, target).success());
		target = relativeURI("n");
		assertTrue(manager.move(source, target).success());
		assertTrue(manager.exists(relativeURI("n/l/f.tmp")));
		assertFalse(manager.exists(relativeURI("l")));

		assumeTrue(manager.create(relativeURI("d/d/d/f.tmp"), new CreateParameters().setMakeParents(true)).success());
		source = relativeURI("d");
		target = relativeURI("o/d");
		assertTrue(manager.move(source, target).success());
		assertTrue(manager.exists(relativeURI("o/d/d/d/d/f.tmp")));
		assertFalse(manager.exists(source));

		source = relativeURI("p.tmp");
		target = relativeURI("root.tmp");
		assertTrue(manager.move(source, target, new MoveParameters().setNoOverwrite()).success());
		assertEquals("File was overwritten in no-overwrite mode", texts.get(target.toString()), read(manager.getInput(target).channel()));

		{
			String originalContent = "Original content";
			source = relativeURI("olderFile.tmp");
			target = relativeURI("newerFile.tmp");
			assumeTrue(manager.create(source).success()); // older file
			prepareData(target, "Original content"); // newer file
			assertTrue("Update mode returned an error", manager.move(source, target, new MoveParameters().setUpdate()).success());
			assertEquals("File was overwritten with older file in update mode", originalContent, read(manager.getInput(target).channel()));
		}

		source = relativeURI("q.tmp");
		target = relativeURI("r");
		result = manager.move(source, target);
		assertTrue(result.success());
		assertTrue(result.getResult(0).getPath().endsWith("r/q.tmp"));

		source = relativeURI("s/*");
		target = relativeURI("t");
		result = manager.move(source, target);
		assertFalse(result.success());

		source = relativeURI("u.tmp");
		target = source;
		result = manager.move(source, target);
		assertFalse(result.success());
		assertTrue(manager.exists(source));
		
		source = relativeURI("samefile/f.tmp");
		target = relativeURI("samefile");
		result = manager.move(source, target);
		assertFalse(result.success());
		assertTrue(manager.exists(source));

		source = relativeURI("samefile/dir");
		target = relativeURI("samefile");
		result = manager.move(source, target);
		assertFalse(result.success());
		assertTrue(manager.exists(source));

		source = relativeURI("samefile/f.tmp");
		target = relativeURI("samefile/./f.tmp");
		result = manager.move(source, target);
		assertFalse(result.success());
		assertTrue(manager.exists(source));
		
		source = relativeURI("samefile/f.tmp");
		target = relativeURI("samefile/../samefile/f.tmp");
		result = manager.move(source, target);
		assertFalse(result.success());
		assertTrue(manager.exists(source));
	}
	
	protected String read(ReadableByteChannel channel) {
		InputStream input = null;
		ByteArrayOutputStream output = null;
		try {
			input = Channels.newInputStream(channel);	
			output = new ByteArrayOutputStream();
			copy(input, output);
		} catch (Throwable t) {
			assumeNoException(t);
		} finally {
			try {
				FileUtils.closeAll(input, output);
			} catch (IOException e) {
				assumeNoException(e);
			}
		}
		return new String(output.toByteArray(), charset);
	}

	protected boolean write(WritableByteChannel channel, String data) {
		InputStream input = null;
		OutputStream output = null;
		try {
			input = new ByteArrayInputStream(data.getBytes(charset));	
			output = Channels.newOutputStream(channel);
			copy(input, output);
		} catch (Throwable t) {
			return false;
		} finally {
			try {
				FileUtils.closeAll(input, output);
			} catch (IOException e) {
				return false;
			}
		}
		return true;
	}

	public void testGetInput() throws Exception {
		Map<String, String> texts = new HashMap<String, String>();
		texts.put("srcdir/file.tmp", "Žluťoučký kůň úpěl ďábelské ódy");
		texts.put("srcdir/f.tmp", "Tak se z lesa ozývá");
		texts.put("srcdir/found.tmp", "V lese padají šišky");
		texts.put("root.tmp", "Root file");
		prepareData(texts);
		assumeTrue(manager.isDirectory(relativeURI("srcdir")));
		
		ReadableByteChannel channel;
		
		for (String path: texts.keySet()) {
			channel = manager.getInput(relativeURI(path)).channel();
			assertEquals(texts.get(path), read(channel));
		}
		
		{
			assertEquals(2, manager.getInput(relativeURI("srcdir/f?*.tmp")).size());
		}
		
		{
			assertEquals(3, manager.getInput(relativeURI("srcdir/f*.tmp")).size());
		}
		
		{
			assertEquals(4, manager.getInput(relativeURI("root.tmp;srcdir/*")).size());
		}
		
		{
			assertEquals(1, manager.getInput(relativeURI("non-existing-file.tmp")).size());
		}

		{
			try {
				manager.getInput(relativeURI("non-existing-file.tmp")).channel();
				fail();
			} catch (Throwable t) {}
		}
	}

	public void testGetOutput() throws Exception {
		Map<String, String> texts = new HashMap<String, String>();
		texts.put("srcdir/file.tmp", "Žluťoučký kůň úpěl ďábelské ódy");
		texts.put("srcdir/f.tmp", "Tak se z lesa ozývá");
		texts.put("srcdir/found.tmp", "V lese padají šišky");
		texts.put("root.tmp", "Root file");

		CloverURI uri = null;
		
		try {
			String path = "srcdir/file.tmp";
			uri = relativeURI(path);
			write(manager.getOutput(uri).channel(), texts.get(path));
			fail();
		} catch (Exception ex) {
			System.out.printf("Caught %s: %s%n", ex, uri);
		}
		
		{
			String path = "root.tmp";
			uri = relativeURI(path);
			write(manager.getOutput(uri).channel(), texts.get(path));
			assertEquals(texts.get(path), read(manager.getInput(uri).channel()));
		}
		
		{
			assumeTrue(manager.create(relativeURI("srcdir/")).success());
		}

		try {
			String path = "srcdir";
			uri = relativeURI(path);
			write(manager.getOutput(uri).channel(), texts.get(path));
			fail();
		} catch (Exception ex) {
			System.out.printf("Caught %s: %s%n", ex, uri);
		}

		for (String path: texts.keySet()) {
			uri = relativeURI(path);
			write(manager.getOutput(uri).channel(), texts.get(path));
			assertEquals(texts.get(path), read(manager.getInput(uri).channel()));
		}
		
		{
			String path = "srcdir/f*.tmp";
			uri = relativeURI(path);
			String newContent = "New content";
			assertEquals(3, manager.getOutput(uri).size());
			WritableContentProvider provider = manager.getOutput(uri);
			for (int i = 0; i < provider.size(); i++) {
				write(provider.channel(i), newContent);
				assertEquals(newContent, read(manager.getInput(relativeURI(provider.getURI(i).toString())).channel()));
			}
			path = "root.tmp";
			assertEquals(texts.get(path), read(manager.getInput(relativeURI(path)).channel()));
		}

		{
			uri = relativeURI("targetdir/");
			manager.create(uri);
			assumeTrue(manager.isDirectory(uri));
			uri = relativeURI("targetdir/nonExistingFile.tmp");
			String newContent = "New content";
			WritableContentProvider provider = manager.getOutput(uri);
			assertEquals(1, provider.size());
			write(provider.channel(), newContent);
			assertEquals(newContent, read(manager.getInput(relativeURI(provider.getURI().toString())).channel()));
		}
	}

	public void testDelete() throws Exception {
		{
			CreateResult result;
			String files;
			files = "eclipse-3.6.1-delta-pack.tmp;eclipse-3.6.2-delta-pack.tmp;eclipse-jee-helios-SR1-win32.tmp;eclipse-jee-helios-SR2-win32.tmp;eclipse-jee-indigo-SR1-win32.tmp;eclipse-jee-indigo-SR2-win32.tmp;org.eclipse.sdk.examples-3.6.2.tmp;visualvm-launcher.tmp";
			result = manager.create(relativeURI("eclipse/", files), new CreateParameters().setMakeParents(true));
			assumeTrue(result.successCount() == files.split(";").length);
			assumeTrue(manager.isDirectory(relativeURI("eclipse")));
			assumeTrue(manager.create(relativeURI("emptyDir/")).success());
			files = "nonEmptyDir1/file.file;nonEmptyDir2/file.file";
			result = manager.create(relativeURI(files), new CreateParameters().setMakeParents(true));
			assumeTrue(result.successCount() == files.split(";").length);
			files = "'wildcardsDir/*.tmp';wildcardsDir/a.tmp";
			result = manager.create(relativeURI(files), new CreateParameters().setMakeParents(true));
			assumeTrue(!result.isEmpty());
			assumeTrue(manager.isFile(relativeURI("wildcardsDir/a.tmp")));
		}
		
		CloverURI uri;
		DeleteResult deleteResult;
		
		uri = relativeURI("eclipse/eclipse-3.6.1-delta-pack.tmp");
		assertTrue(String.format("%s is not a file", uri), manager.isFile(uri));
		deleteResult = manager.delete(uri);
		assertTrue(String.format("Delete %s failed", uri), deleteResult.success());
		assertEquals(1, deleteResult.totalCount());
		assertEquals(1, deleteResult.successCount());
		assertEquals(0, deleteResult.errorCount());
		assertFalse(String.format("%s still exists", uri), manager.exists(uri));
		
		uri = relativeURI("eclipse/eclipse-3.6.1-delta-pack.zi");
		assertFalse(String.format("%s exists", uri), manager.exists(uri));
		deleteResult = manager.delete(uri);
		assertFalse(String.format("Delete non-existing files must not succeed", uri), deleteResult.success());
		assertEquals(1, deleteResult.totalCount());
		assertEquals(0, deleteResult.successCount());
		assertEquals(1, deleteResult.errorCount());
		
		uri = relativeURI("emptyDir");
		assertTrue(String.format("%s is not a directory", uri), manager.isDirectory(uri));
		assertTrue(String.format("Delete %s failed", uri), manager.delete(uri).success());
		assertFalse(String.format("%s still exists", uri), manager.exists(uri));

		uri = relativeURI("eclipse");
		assertTrue(String.format("%s is not a directory", uri), manager.isDirectory(uri));
		assertFalse(String.format("Non-recursive delete of a non-empty dir %s succeeded", uri), manager.delete(uri).success());
		assertTrue(String.format("%s does not exist anymore", uri), manager.exists(uri));

		uri = relativeURI("eclipse/eclipse*.tmp");
		assertFalse(String.format("%s resolves to an empty list", uri), manager.resolve(uri).isEmpty());
		assertTrue(String.format("Delete %s failed", uri), manager.delete(uri).success());
		assertTrue("Some files were not deleted", manager.list(uri).isEmpty());
		assertTrue("Wrong file was deleted", manager.exists(relativeURI("eclipse/visualvm-launcher.tmp")));

		uri = relativeURI("eclipse");
		assertTrue(String.format("%s is not a directory", uri), manager.isDirectory(uri));
		assertFalse(String.format("Non-recursive delete of a non-empty dir %s succeeded", uri), manager.delete(uri).success());
		assertTrue(String.format("Recursive delete of %s failed", uri), manager.delete(uri, new DeleteParameters().setRecursive(true)).success());
		assertFalse(String.format("%s still exists", uri), manager.exists(uri));

		uri = relativeURI("nonEmptyDir?");
		assertFalse(String.format("%s resolves to an empty list", uri), manager.resolve(uri).isEmpty());
		assertFalse(String.format("Non-recursive delete of a non-empty dir %s succeeded", uri), manager.delete(uri).success());
		assertTrue(String.format("Recursive delete of %s failed", uri), manager.delete(uri, new DeleteParameters().setRecursive(true)).success());
		assertTrue(String.format("%s still exists", uri), manager.resolve(uri).isEmpty());
		
		if (manager.isFile(relativeURI("'wildcardsDir/*.tmp'"))) {
			assertTrue(String.format("%s does not exist", uri), manager.isFile(relativeURI("wildcardsDir/a.tmp")));
			assertTrue(String.format("Delete %s failed", uri), manager.delete(relativeURI("'wildcardsDir/*.tmp'")).success());
			assertTrue(String.format("Wrong file deleted", uri), manager.isFile(relativeURI("wildcardsDir/a.tmp")));
		} else {
			System.err.println("Could not test deleting 'wildcardsDir/*.tmp'");
		}
	}

	public void testResolve() throws Exception {
		{
			String files = "eclipse/eclipse-3.6.1-delta-pack.tmp;eclipse/eclipse-3.6.2-delta-pack.tmp;eclipse/eclipse-jee-helios-SR1-win32.tmp;eclipse/eclipse-jee-helios-SR2-win32.tmp;eclipse/eclipse-jee-indigo-SR1-win32.tmp;eclipse/eclipse-jee-indigo-SR2-win32.tmp;eclipse/org.eclipse.sdk.examples-3.6.2.tmp;eclipse/visualvm-launcher.tmp";
			CreateResult result = manager.create(relativeURI(files), new CreateParameters().setMakeParents(true));
			assumeTrue(result.successCount() == files.split(";").length);
			assumeTrue(manager.isDirectory(relativeURI("eclipse")));
			assumeTrue(manager.create(relativeURI("aaa/eclipse-3.7/eclipse.exe;bbb/eclipse-3.6/eclipse.exe;bbb/eclipse-3.6/eclipse.tmp"), new CreateParameters().setMakeParents(true)).success());
			
			assumeTrue(manager.create(relativeURI("subdir/tmpdir/"), new CreateParameters().setMakeParents(true)).success());
			assumeTrue(manager.isDirectory(relativeURI("subdir/tmpdir")));
			assumeTrue(manager.create(relativeURI("subdir/tmpfile.tmp")).success());
			assumeTrue(manager.isFile(relativeURI("subdir/tmpfile.tmp")));
		}
		
		ResolveResult result;
		result = manager.resolve(relativeURI("e??ipse/eclipse*.tmp"));
		System.out.println(result);
		assertTrue(result.success());
		assertEquals(6, result.successCount());
		
		
		result = manager.resolve(relativeURI("*\\ec?ipse-?.?\\eclipse.exe"));
		System.out.println(result);
		assertTrue(result.success());
		assertEquals(2, result.successCount());

		result = manager.resolve(relativeURI("subdir/*/"));
		System.out.println(result);
		assertTrue(result.success());
		assertEquals(1, result.successCount());
	}
	
	protected List<String> getRelativePaths(URI base, List<Info> infos) {
		List<String> relatives = new ArrayList<String>();
		for (Info info: infos) {
			String relative = base.relativize(info.getURI()).toString();
			if (info.isDirectory() && !relative.endsWith("/")) {
				relative = relative + "/";
			}
			relatives.add(relative);
		}
		return relatives;
	}
	
	protected void printInfo(URI base, List<Info> infos) {
		int[] columns = new int[3];
		for (Info info: infos) {
			columns[0] = Math.max(columns[0], base.relativize(info.getURI()).toString().length());
			columns[1] = Math.max(columns[1], DATE_FORMAT.format(info.getLastModified()).length());
			columns[2] = Math.max(columns[2], String.valueOf(info.getSize()).length());
		}
		String format = String.format(" %%s %%-%ds   %%%ds   %%%dd B%%n", columns[0], columns[1], columns[2]);
		System.out.printf("Retrieved contents of %s:%n", base);
		for (Info info: infos) {
			System.out.printf(format, info.isDirectory() ? "D" : "F", base.relativize(info.getURI()), DATE_FORMAT.format(info.getLastModified()), info.getSize());
		}
	}

	public void testList() throws Exception {
		{
			CreateResult result;
			String files;
			files = "eclipse-3.6.1-delta-pack.tmp;eclipse-3.6.2-delta-pack.tmp;eclipse-jee-helios-SR1-win32.tmp;eclipse-jee-helios-SR2-win32.tmp;eclipse-jee-indigo-SR1-win32.tmp;eclipse-jee-indigo-SR2-win32.tmp;org.eclipse.sdk.examples-3.6.2.tmp;visualvm-launcher.tmp";
			result = manager.create(relativeURI("dir1/eclipse/", files), new CreateParameters().setMakeParents(true));
			assumeTrue(result.successCount() == files.split(";").length);
			assumeTrue(manager.isDirectory(relativeURI("dir1/eclipse")));
			files = "topdir/file;topdir/subdir/";
			result = manager.create(relativeURI("dir1/", files), new CreateParameters().setMakeParents(true));
			assumeTrue(result.successCount() == files.split(";").length);
			assumeTrue(manager.isFile(relativeURI("dir1/", "topdir/file")));
			assumeTrue(manager.isDirectory(relativeURI("dir1/", "topdir/subdir/")));
			files = "indigo/eclipse.tmp;information/readme.txt;dummy/dummy.file";
			result = manager.create(relativeURI("dir2-Install/", files), new CreateParameters().setMakeParents(true));
			assumeTrue(result.successCount() == files.split(";").length);
			result = manager.create(relativeURI("dir2-Insight/", files), new CreateParameters().setMakeParents(true));
			assumeTrue(result.successCount() == files.split(";").length);
		}
		
		List<Info> result;

		result = manager.list(relativeURI("dir1")).getResult();
		printInfo(baseUri.resolve("dir1"), result);
		assertEquals("Different list result", new HashSet<String>(Arrays.asList("eclipse/;topdir/".split(";"))), new HashSet<String>(getRelativePaths(baseUri.resolve("dir1"), result)));
		for (Info info: result) {
			if (info.getName().equals("eclipse") 
					|| info.getName().equals("topdir")
					|| info.getName().equals("subdir")) {
				assertTrue(String.format("%s is not a directory", info.getURI()), info.isDirectory());
				assertFalse(String.format("%s should not be a file", info.getURI()), info.isFile());
			} else {
				assertTrue(String.format("%s is not a file", info.getURI()), info.isFile());
				assertFalse(String.format("%s should not be a directory", info.getURI()), info.isDirectory());
			}
		}
		
		result = manager.list(relativeURI("dir1"), new ListParameters().setRecursive(true)).getResult();
		printInfo(baseUri.resolve("dir1"), result);
		assertEquals("Different recursive list result", new HashSet<String>(Arrays.asList("eclipse/;eclipse/eclipse-3.6.1-delta-pack.tmp;eclipse/eclipse-3.6.2-delta-pack.tmp;eclipse/eclipse-jee-helios-SR1-win32.tmp;eclipse/eclipse-jee-helios-SR2-win32.tmp;eclipse/eclipse-jee-indigo-SR1-win32.tmp;eclipse/eclipse-jee-indigo-SR2-win32.tmp;eclipse/org.eclipse.sdk.examples-3.6.2.tmp;eclipse/visualvm-launcher.tmp;topdir/;topdir/file;topdir/subdir/".split(";"))), new HashSet<String>(getRelativePaths(baseUri.resolve("dir1"), result)));
		for (Info info: result) {
			if (info.getName().equals("eclipse") 
					|| info.getName().equals("topdir")
					|| info.getName().equals("subdir")) {
				assertTrue(String.format("%s is not a directory", info.getURI()), info.isDirectory());
				assertFalse(String.format("%s should not be a file", info.getURI()), info.isFile());
			} else {
				assertTrue(String.format("%s is not a file", info.getURI()), info.isFile());
				assertFalse(String.format("%s should not be a directory", info.getURI()), info.isDirectory());
			}
		}
		
		result = manager.list(relativeURI("dir1/top?ir"), new ListParameters().setRecursive(true)).getResult();
		printInfo(baseUri.resolve("dir1/topdir"), result);
		assertEquals("Different recursive list result", new HashSet<String>(Arrays.asList("file;subdir/".split(";"))), new HashSet<String>(getRelativePaths(baseUri.resolve("dir1/topdir"), result)));
		for (Info info: result) {
			if (info.getName().equals("subdir")) {
				assertTrue(String.format("%s is not a directory", info.getURI()), info.isDirectory());
				assertFalse(String.format("%s should not be a file", info.getURI()), info.isFile());
			} else {
				assertTrue(String.format("%s is not a file", info.getURI()), info.isFile());
				assertFalse(String.format("%s should not be a directory", info.getURI()), info.isDirectory());
			}
		}

		result = manager.list(relativeURI("dir2-Ins*/i*"), new ListParameters().setRecursive(true)).getResult();
		System.out.println(result);
		assertEquals("Different list result", new HashSet<String>(Arrays.asList("dir2-Install/indigo/eclipse.tmp;dir2-Install/information/readme.txt;dir2-Insight/indigo/eclipse.tmp;dir2-Insight/information/readme.txt".split(";"))), new HashSet<String>(getRelativePaths(baseUri, result)));
		printInfo(baseUri, result);

		result = manager.list(relativeURI("dir2-Install"), new ListParameters().setRecursive(true)).getResult();
		System.out.println(result);
		assertEquals("Different list result", new HashSet<String>(Arrays.asList("dir2-Install/dummy/;dir2-Install/dummy/dummy.file;dir2-Install/indigo/;dir2-Install/indigo/eclipse.tmp;dir2-Install/information/;dir2-Install/information/readme.txt".split(";"))), new HashSet<String>(getRelativePaths(baseUri, result)));
		printInfo(baseUri, result);

		{
			ListResult listResult = manager.list(relativeURI("non-existing-dir;dir1/topdir/file;dir1/topdir/file/"), new ListParameters().setRecursive(true));
			assertEquals(3, listResult.totalCount());
			assertEquals(1, listResult.successCount());
			assertEquals(2, listResult.errorCount());
		}
		
	}
	
	protected CloverURI relativeURI(String uri) throws URISyntaxException {
		return CloverURI.createRelativeURI(baseUri, uri);
	}

	protected CloverURI relativeURI(String commonPart, String uri) throws URISyntaxException {
		return CloverURI.createRelativeURI(baseUri.resolve(commonPart), uri);
	}

	public void testCreate() throws Exception {
		CloverURI uri;
		Date modifiedDate = new Date(10000);
		
		uri = relativeURI("file");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(String.format("Failed to create %s", uri), manager.create(uri).success());
		assertTrue(String.format("%s is not a file", uri), manager.isFile(uri));
		
		uri = relativeURI("topdir1/subdir/subsubdir/file");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertFalse(manager.create(uri).success());
		assertFalse(String.format("Created %s even though the parent dir did not exist", uri), manager.exists(uri));
		
		uri = relativeURI("topdir1/subdir/subsubdir/file");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(manager.create(uri, new CreateParameters().setMakeParents(true)).success());
		assertTrue(String.format("%s is a not file", uri), manager.isFile(uri));
		assertTrue(manager.create(uri, new CreateParameters().setLastModified(modifiedDate)).success());
		assertEquals("Dates are different", modifiedDate, manager.info(uri).getLastModified());
		
		uri = relativeURI("topdir2/subdir/subsubdir/dir");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertFalse(manager.create(uri, new CreateParameters().setDirectory(true)).success());
		assertFalse(String.format("Created %s even though the parent dir did not exist", uri), manager.exists(uri));

		uri = relativeURI("topdir2/subdir/subsubdir/dir");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(String.format("Failed to create %s", uri), manager.create(uri, new CreateParameters().setDirectory(true).setMakeParents(true)).success());
		assertTrue(String.format("%s is not a directory", uri), manager.isDirectory(uri));
		
		uri = relativeURI("topdir2/subdir/subsubdir/dir2/");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(manager.create(uri, new CreateParameters().setMakeParents(true)).success());
		assertTrue(String.format("%s is not a directory", uri), manager.isDirectory(uri));
		assertTrue(manager.create(uri, new CreateParameters().setLastModified(modifiedDate)).success());
		assertEquals("Dates are different", modifiedDate, manager.info(uri).getLastModified());
		
		uri = relativeURI("datedFile");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(manager.create(uri, new CreateParameters().setMakeParents(true)).success());
		assertTrue(String.format("%s is not a file", uri), manager.isFile(uri));
		assertTrue(manager.create(uri, new CreateParameters().setLastModified(modifiedDate)).success());
		assertEquals("Dates are different", modifiedDate, manager.info(uri).getLastModified());
		
		uri = relativeURI("datedDir1");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(manager.create(uri, new CreateParameters().setDirectory(true).setLastModified(modifiedDate)).success());
		assertTrue(String.format("%s is not a directory", uri), manager.isDirectory(uri));
		assertEquals("Dates are different", modifiedDate, manager.info(uri).getLastModified());

		uri = relativeURI("datedDir2/");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(manager.create(uri, new CreateParameters().setLastModified(modifiedDate)).success());
		assertTrue(String.format("%s is not a directory", uri), manager.isDirectory(uri));
		assertEquals("Dates are different", modifiedDate, manager.info(uri).getLastModified());
		
		uri = relativeURI("fileShouldBeDirectory");
		System.out.println(uri.getAbsoluteURI());
		assertFalse(String.format("%s already exists", uri), manager.exists(uri));
		assertTrue(manager.create(uri).success());
		assertTrue(String.format("%s is not a file", uri), manager.isFile(uri));
		assertFalse(manager.create(uri, new CreateParameters().setDirectory(true)).success());
		assertTrue(String.format("%s is not a file", uri), manager.isFile(uri));
		assertFalse(String.format("%s is a directory", uri), manager.isDirectory(uri));
		uri = relativeURI("fileShouldBeDirectory/");
		assertFalse(manager.create(uri).success());
		assertTrue(String.format("%s is not a file", uri), manager.isFile(uri));
		assertFalse(String.format("%s is a directory", uri), manager.isDirectory(uri));

	}
	
//	public void testInterruptCreate() throws Exception {
//		assumeTrue(!Thread.currentThread().isInterrupted());
//		
//		StringBuilder sb = new StringBuilder();
//		for (int i = 0; i < 100; i++) {
//			sb.append("a/");
//		}
//		CloverURI uri = relativeURI(sb.toString());
//		Thread.currentThread().interrupt();
//		CreateResult result = manager.create(uri, new CreateParameters().setMakeParents(true));
//		assertFalse(result.success());
//		assertEquals("Interrupted", result.getError(0));
//	}

	protected static final int MAX_DEPTH = 3;
	
	protected void generate(URI root, int depth) throws IOException {
		if (depth > MAX_DEPTH) {
			return;
		}
		int i = 0;
		for ( ; i < 3; i++) {
			String name = String.valueOf(i);
			URI child = URIUtils.getChildURI(root, name);
			manager.create(CloverURI.createSingleURI(child), new CreateParameters().setDirectory(true));
			generate(child, depth+1);
		}
		for ( ; i < 20; i++) {
			String name = String.valueOf(i);
			URI child = URIUtils.getChildURI(root, name);
			manager.create(CloverURI.createSingleURI(child));
		}
	}
	
	protected void generate(URI root) throws IOException {
		generate(root, 0);
	}
	
	public void testInterruptDelete() throws Exception {
		Thread mainThread = Thread.currentThread();
		assumeTrue(!mainThread.isInterrupted());

		CloverURI uri = relativeURI("InterruptDelete");
		manager.create(uri, new CreateParameters().setDirectory(true));
		generate(uri.getAbsoluteURI().getSingleURI().toURI());
		
		mainThread.interrupt();
		DeleteResult result = manager.delete(uri, new DeleteParameters().setRecursive(true));
		assertFalse(result.success());
//		assertEquals("Interrupted", result.getError(0));
	}

	public void testInterruptCopy() throws Exception {
		Thread mainThread = Thread.currentThread();
		assumeTrue(!mainThread.isInterrupted());

		CloverURI source = relativeURI("InterruptCopy");
		CloverURI target = relativeURI("InterruptCopy_copy");
		manager.create(source, new CreateParameters().setDirectory(true));
		generate(source.getAbsoluteURI().getSingleURI().toURI());
		
		mainThread.interrupt();
		CopyResult result = manager.copy(source, target, new CopyParameters().setRecursive(true));
		assertFalse(result.success());
//		assertEquals("Interrupted", result.getError(0));
	}

	public void testInterruptMove() throws Exception {
		Thread mainThread = Thread.currentThread();
		assumeTrue(!mainThread.isInterrupted());

		CloverURI source = relativeURI("InterruptMove");
		CloverURI target = relativeURI("InterruptMove_new");
		manager.create(source, new CreateParameters().setDirectory(true));
		generate(source.getAbsoluteURI().getSingleURI().toURI());
		
		mainThread.interrupt();
		MoveResult result = manager.move(source, target);
		assertFalse(result.success());
//		assertEquals("Interrupted", result.getError(0));
	}

	public void testInterruptList() throws Exception {
		Thread mainThread = Thread.currentThread();
		assumeTrue(!mainThread.isInterrupted());

		CloverURI uri = relativeURI("InterruptList");
		manager.create(uri, new CreateParameters().setDirectory(true));
		generate(uri.getAbsoluteURI().getSingleURI().toURI());
		
		mainThread.interrupt();
		ListResult result = manager.list(uri, new ListParameters().setRecursive(true));
		assertFalse(result.success());
//		assertEquals("Interrupted", result.getError(0));
	}
}