//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Part;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;



/**
 * MultiPartInputStream
 *
 * Handle a MultiPart Mime input stream, breaking it up on the boundary into files and strings.
 */
public class MultiPartInputStream
{
    private static final Logger LOG = Log.getLogger(MultiPartInputStream.class);

    public static final MultipartConfigElement  __DEFAULT_MULTIPART_CONFIG = new MultipartConfigElement(System.getProperty("java.io.tmpdir"));
    protected InputStream _in;
    protected MultipartConfigElement _config;
    protected String _contentType;
    protected MultiMap<String> _parts;
    protected File _tmpDir;
    protected File _contextTmpDir;
    protected boolean _deleteOnExit;
    
    
    
    public class MultiPart implements Part
    {
        protected String _name;
        protected String _filename;
        protected File _file;
        protected OutputStream _out;
        protected ByteArrayOutputStream2 _bout;
        protected String _contentType;
        protected MultiMap<String> _headers;
        protected long _size = 0;
        protected boolean _temporary = true;

        public MultiPart (String name, String filename) 
        throws IOException
        {
            _name = name;
            _filename = filename;
        }

        protected void setContentType (String contentType)
        {
            _contentType = contentType;
        }
        
        
        protected void open() 
        throws IOException
        {
            //We will either be writing to a file, if it has a filename on the content-disposition
            //and otherwise a byte-array-input-stream, OR if we exceed the getFileSizeThreshold, we
            //will need to change to write to a file.           
            if (_filename != null && _filename.trim().length() > 0)
            {
                createFile();            
            }
            else
            {
                //Write to a buffer in memory until we discover we've exceed the 
                //MultipartConfig fileSizeThreshold
                _out = _bout= new ByteArrayOutputStream2();
            }
        }
        
        protected void close() 
        throws IOException
        {
            _out.close();
        }
        
      
        protected void write (int b)
        throws IOException
        {      
            if (MultiPartInputStream.this._config.getMaxFileSize() > 0 && _size + 1 > MultiPartInputStream.this._config.getMaxFileSize())
                throw new IllegalStateException ("Multipart Mime part "+_name+" exceeds max filesize");
            
            if (MultiPartInputStream.this._config.getFileSizeThreshold() > 0 && _size + 1 > MultiPartInputStream.this._config.getFileSizeThreshold() && _file==null)
                createFile();
            _out.write(b);   
            _size ++;
        }
        
        protected void write (byte[] bytes, int offset, int length) 
        throws IOException
        { 
            if (MultiPartInputStream.this._config.getMaxFileSize() > 0 && _size + length > MultiPartInputStream.this._config.getMaxFileSize())
                throw new IllegalStateException ("Multipart Mime part "+_name+" exceeds max filesize");
            
            if (MultiPartInputStream.this._config.getFileSizeThreshold() > 0 && _size + length > MultiPartInputStream.this._config.getFileSizeThreshold() && _file==null)
                createFile();
            
            _out.write(bytes, offset, length);
            _size += length;
        }
        
        protected void createFile ()
        throws IOException
        {
            _file = File.createTempFile("MultiPart", "", MultiPartInputStream.this._tmpDir);
            if (_deleteOnExit)
                _file.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(_file);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            
            if (_size > 0 && _out != null)
            {
                //already written some bytes, so need to copy them into the file
                _out.flush();
                _bout.writeTo(bos);
                _out.close();
                _bout = null;
            }
            _out = bos;
        }
        

        
        protected void setHeaders(MultiMap<String> headers)
        {
            _headers = headers;
        }
        
        /** 
         * @see javax.servlet.http.Part#getContentType()
         */
        public String getContentType()
        {
            return _contentType;
        }

        /** 
         * @see javax.servlet.http.Part#getHeader(java.lang.String)
         */
        public String getHeader(String name)
        {
            if (name == null)
                return null;
            return (String)_headers.getValue(name.toLowerCase(Locale.ENGLISH), 0);
        }

        /** 
         * @see javax.servlet.http.Part#getHeaderNames()
         */
        public Collection<String> getHeaderNames()
        {
            return _headers.keySet();
        }

        /** 
         * @see javax.servlet.http.Part#getHeaders(java.lang.String)
         */
        public Collection<String> getHeaders(String name)
        {
           return _headers.getValues(name);
        }

        /** 
         * @see javax.servlet.http.Part#getInputStream()
         */
        public InputStream getInputStream() throws IOException
        {
           if (_file != null)
           {
               //written to a file, whether temporary or not
               return new BufferedInputStream (new FileInputStream(_file));
           }
           else
           {
               //part content is in memory
               return new ByteArrayInputStream(_bout.getBuf(),0,_bout.size());
           }
        }

        public byte[] getBytes()
        {
            if (_bout!=null)
                return _bout.toByteArray();
            return null;
        }
        
        /** 
         * @see javax.servlet.http.Part#getName()
         */
        public String getName()
        {
           return _name;
        }

        /** 
         * @see javax.servlet.http.Part#getSize()
         */
        public long getSize()
        {
            return _size;         
        }

        /** 
         * @see javax.servlet.http.Part#write(java.lang.String)
         */
        public void write(String fileName) throws IOException
        {
            if (_file == null)
            {
                _temporary = false;
                
                //part data is only in the ByteArrayOutputStream and never been written to disk
                _file = new File (_tmpDir, fileName);

                BufferedOutputStream bos = null;
                try
                {
                    bos = new BufferedOutputStream(new FileOutputStream(_file));
                    _bout.writeTo(bos);
                    bos.flush();
                }
                finally
                {
                    if (bos != null)
                        bos.close();
                    _bout = null;
                }
            }
            else
            {
                //the part data is already written to a temporary file, just rename it
                _temporary = false;
                
                File f = new File(_tmpDir, fileName);
                if (_file.renameTo(f))
                    _file = f;
            }
        }
        
        /** 
         * Remove the file, whether or not Part.write() was called on it
         * (ie no longer temporary)
         * @see javax.servlet.http.Part#delete()
         */
        public void delete() throws IOException
        {
            if (_file != null && _file.exists())
                _file.delete();     
        }
        
        /**
         * Only remove tmp files.
         * 
         * @throws IOException
         */
        public void cleanUp() throws IOException
        {
            if (_temporary && _file != null && _file.exists())
                _file.delete();
        }
        
        
        /**
         * Get the file, if any, the data has been written to.
         * @return
         */
        public File getFile ()
        {
            return _file;
        }  
        
        
        /**
         * Get the filename from the content-disposition.
         * @return null or the filename
         */
        public String getContentDispositionFilename ()
        {
            return _filename;
        }
    }
    
    
    
    
    /**
     * @param in Request input stream 
     * @param contentType Content-Type header
     * @param config MultipartConfigElement 
     * @param contextTmpDir javax.servlet.context.tempdir
     */
    public MultiPartInputStream (InputStream in, String contentType, MultipartConfigElement config, File contextTmpDir)
    {
        _in = new ReadLineInputStream(in);
       _contentType = contentType;
       _config = config;
       _contextTmpDir = contextTmpDir;
       if (_contextTmpDir == null)
           _contextTmpDir = new File (System.getProperty("java.io.tmpdir"));
       
       if (_config == null)
           _config = new MultipartConfigElement(_contextTmpDir.getAbsolutePath());
    }

    /**
     * Get the already parsed parts.
     * 
     * @return
     */
    public Collection<Part> getParsedParts()
    {
        if (_parts == null)
            return Collections.emptyList();

        Collection<Object> values = _parts.values();
        List<Part> parts = new ArrayList<Part>();
        for (Object o: values)
        {
            List<Part> asList = LazyList.getList(o, false);
            parts.addAll(asList);
        }
        return parts;
    }
    
    /**
     * Delete any tmp storage for parts, and clear out the parts list.
     * 
     * @throws MultiException
     */
    public void deleteParts ()
    throws MultiException
    {
        Collection<Part> parts = getParsedParts();
        MultiException err = new MultiException();
        for (Part p:parts)
        {
            try
            {
                ((MultiPartInputStream.MultiPart)p).cleanUp();
            } 
            catch(Exception e)
            {     
                err.add(e); 
            }
        }
        _parts.clear();
        
        err.ifExceptionThrowMulti();
    }

   
    /**
     * Parse, if necessary, the multipart data and return the list of Parts.
     * 
     * @return
     * @throws IOException
     * @throws ServletException
     */
    public Collection<Part> getParts()
    throws IOException, ServletException
    {
        parse();
        Collection<Object> values = _parts.values();
        List<Part> parts = new ArrayList<Part>();
        for (Object o: values)
        {
            List<Part> asList = LazyList.getList(o, false);
            parts.addAll(asList);
        }
        return parts;
    }
    
    
    /**
     * Get the named Part.
     * 
     * @param name
     * @return
     * @throws IOException
     * @throws ServletException
     */
    public Part getPart(String name)
    throws IOException, ServletException
    {
        parse();
        return (Part)_parts.getValue(name, 0);
    }
    
    
    /**
     * Parse, if necessary, the multipart stream.
     * 
     * @throws IOException
     * @throws ServletException
     */
    protected void parse ()
    throws IOException, ServletException
    {
        //have we already parsed the input?
        if (_parts != null)
            return;
        
        //initialize
        long total = 0; //keep running total of size of bytes read from input and throw an exception if exceeds MultipartConfigElement._maxRequestSize              
        _parts = new MultiMap<String>();

        //if its not a multipart request, don't parse it
        if (_contentType == null || !_contentType.startsWith("multipart/form-data"))
            return;
 
        //sort out the location to which to write the files
        
        if (_config.getLocation() == null)
            _tmpDir = _contextTmpDir;
        else if ("".equals(_config.getLocation()))
            _tmpDir = _contextTmpDir;
        else
        {
            File f = new File (_config.getLocation());
            if (f.isAbsolute())
                _tmpDir = f;
            else
                _tmpDir = new File (_contextTmpDir, _config.getLocation());
        }
      
        if (!_tmpDir.exists())
            _tmpDir.mkdirs();

        String contentTypeBoundary = "";
        if (_contentType.indexOf("boundary=") >= 0)
            contentTypeBoundary = QuotedStringTokenizer.unquote(value(_contentType.substring(_contentType.indexOf("boundary=")), true).trim());
        
        String boundary="--"+contentTypeBoundary;
        byte[] byteBoundary=(boundary+"--").getBytes(StringUtil.__ISO_8859_1);

        // Get first boundary
        String line=((ReadLineInputStream)_in).readLine();

        if (line == null)
            throw new IOException("Missing content for multipart request");

        boolean badFormatLogged = false;
        line=line.trim();
        while (line != null && !line.equals(boundary))
        {
            if (!badFormatLogged)
            {
                LOG.warn("Badly formatted multipart request");
                badFormatLogged = true;
            }
            line=((ReadLineInputStream)_in).readLine();
            line=(line==null?line:line.trim());
        }

        if (line == null)
            throw new IOException("Missing initial multi part boundary");

        // Read each part
        boolean lastPart=false;
        String contentDisposition=null;
        String contentType=null;
        String contentTransferEncoding=null;
        outer:while(!lastPart)
        {
            MultiMap<String> headers = new MultiMap<String>();
            while(true)
            {
                line=((ReadLineInputStream)_in).readLine();
                
                //No more input
                if(line==null)
                    break outer;

                // If blank line, end of part headers
                if("".equals(line))
                    break;
                
                total += line.length();
                if (_config.getMaxRequestSize() > 0 && total > _config.getMaxRequestSize())
                    throw new IllegalStateException ("Request exceeds maxRequestSize ("+_config.getMaxRequestSize()+")");

                //get content-disposition and content-type
                int c=line.indexOf(':',0);
                if(c>0)
                {
                    String key=line.substring(0,c).trim().toLowerCase(Locale.ENGLISH);
                    String value=line.substring(c+1,line.length()).trim();
                    headers.put(key, value);
                    if (key.equalsIgnoreCase("content-disposition"))
                        contentDisposition=value;
                    if (key.equalsIgnoreCase("content-type"))
                        contentType = value;
                    if(key.equals("content-transfer-encoding"))
                        contentTransferEncoding=value;

                }
            }

            // Extract content-disposition
            boolean form_data=false;
            if(contentDisposition==null)
            {
                throw new IOException("Missing content-disposition");
            }

            QuotedStringTokenizer tok=new QuotedStringTokenizer(contentDisposition,";", false, true);
            String name=null;
            String filename=null;
            while(tok.hasMoreTokens())
            {
                String t=tok.nextToken().trim();
                String tl=t.toLowerCase(Locale.ENGLISH);
                if(t.startsWith("form-data"))
                    form_data=true;
                else if(tl.startsWith("name="))
                    name=value(t, true);
                else if(tl.startsWith("filename="))
                    filename=filenameValue(t);
            }

            // Check disposition
            if(!form_data)
            {
                continue;
            }
            //It is valid for reset and submit buttons to have an empty name.
            //If no name is supplied, the browser skips sending the info for that field.
            //However, if you supply the empty string as the name, the browser sends the
            //field, with name as the empty string. So, only continue this loop if we
            //have not yet seen a name field.
            if(name==null)
            {
                continue;
            }

            if ("base64".equalsIgnoreCase(contentTransferEncoding))
            {
                _in = new Base64InputStream(_in);
            }
            else if ("quoted-printable".equalsIgnoreCase(contentTransferEncoding))
            {
                _in = new FilterInputStream(_in)
                {
                    @Override
                    public int read() throws IOException
                    {
                        int c = in.read();
                        if (c >= 0 && c == '=')
                        {
                            int hi = in.read();
                            int lo = in.read();
                            if (hi < 0 || lo < 0)
                            {
                                throw new IOException("Unexpected end to quoted-printable byte");
                            }
                            char[] chars = new char[] { (char)hi, (char)lo };
                            c = Integer.parseInt(new String(chars),16);
                        }
                        return c;
                    }
                };
            }

            
            
            //Have a new Part
            MultiPart part = new MultiPart(name, filename);
            part.setHeaders(headers);
            part.setContentType(contentType);
            _parts.add(name, part);

            part.open();

            try
            { 
                int state=-2;
                int c;
                boolean cr=false;
                boolean lf=false;

                // loop for all lines
                while(true)
                {
                    int b=0;
                    while((c=(state!=-2)?state:_in.read())!=-1)
                    {
                        total ++;
                        if (_config.getMaxRequestSize() > 0 && total > _config.getMaxRequestSize())
                            throw new IllegalStateException("Request exceeds maxRequestSize ("+_config.getMaxRequestSize()+")");
                        
                        state=-2;
                        // look for CR and/or LF
                        if(c==13||c==10)
                        {
                            if(c==13)
                            {
                                _in.mark(1);
                                int tmp=_in.read();
                                if (tmp!=10)
                                    _in.reset();
                                else
                                    state=tmp;
                            }
                            break;
                        }
                        // look for boundary
                        if(b>=0&&b<byteBoundary.length&&c==byteBoundary[b])
                            b++;
                        else
                        {
                            // this is not a boundary
                            if(cr)
                                part.write(13);
                    
                            if(lf)
                                part.write(10); 
                            
                            cr=lf=false;
                            if(b>0)
                                part.write(byteBoundary,0,b);
                              
                            b=-1;
                            part.write(c);
                        }
                    }
                    // check partial boundary
                    if((b>0&&b<byteBoundary.length-2)||(b==byteBoundary.length-1))
                    {
                        if(cr)
                            part.write(13);

                        if(lf)
                            part.write(10);

                        cr=lf=false;
                        part.write(byteBoundary,0,b);
                        b=-1;
                    }
                    // boundary match
                    if(b>0||c==-1)
                    {
                        if(b==byteBoundary.length)
                            lastPart=true;
                        if(state==10)
                            state=-2;
                        break;
                    }
                    // handle CR LF
                    if(cr)
                        part.write(13); 

                    if(lf)
                        part.write(10);

                    cr=(c==13);
                    lf=(c==10||state==10);
                    if(state==10)
                        state=-2;
                }
            }
            finally
            {
 
                part.close();
            }
        }
        if (!lastPart)
            throw new IOException("Incomplete parts");
    }
    
    public void setDeleteOnExit(boolean deleteOnExit)
    {
        _deleteOnExit = deleteOnExit;
    }


    public boolean isDeleteOnExit()
    {
        return _deleteOnExit;
    }


    /* ------------------------------------------------------------ */
    private String value(String nameEqualsValue, boolean splitAfterSpace)
    {
        /*
        String value=nameEqualsValue.substring(nameEqualsValue.indexOf('=')+1).trim();
        int i=value.indexOf(';');
        if(i>0)
            value=value.substring(0,i);
        if(value.startsWith("\""))
        {
            value=value.substring(1,value.indexOf('"',1));
        }
        else if (splitAfterSpace)
        {
            i=value.indexOf(' ');
            if(i>0)
                value=value.substring(0,i);
        }
        return value;
        */
         int idx = nameEqualsValue.indexOf('=');
         String value = nameEqualsValue.substring(idx+1).trim();
         return QuotedStringTokenizer.unquoteOnly(value);
    }
    
    
    /* ------------------------------------------------------------ */
    private String filenameValue(String nameEqualsValue)
    {
        int idx = nameEqualsValue.indexOf('=');
        String value = nameEqualsValue.substring(idx+1).trim();   

        if (value.matches(".??[a-z,A-Z]\\:\\\\[^\\\\].*"))
        {
            //incorrectly escaped IE filenames that have the whole path
            //we just strip any leading & trailing quotes and leave it as is
            char first=value.charAt(0);
            if (first=='"' || first=='\'')
                value=value.substring(1);
            char last=value.charAt(value.length()-1);
            if (last=='"' || last=='\'')
                value = value.substring(0,value.length()-1);

            return value;
        }
        else
            //unquote the string, but allow any backslashes that don't
            //form a valid escape sequence to remain as many browsers
            //even on *nix systems will not escape a filename containing
            //backslashes
            return QuotedStringTokenizer.unquoteOnly(value, true);
    }
    
    private static class Base64InputStream extends InputStream
    {
        BufferedReader _in;
        String _line;
        byte[] _buffer;
        int _pos;

        public Base64InputStream (InputStream in)
        {
            _in = new BufferedReader(new InputStreamReader(in));
        }

        @Override
        public int read() throws IOException
        {
            if (_buffer==null || _pos>= _buffer.length)
            {
                _line = _in.readLine();
                if (_line==null)
                    return -1;
                if (_line.startsWith("--"))
                    _buffer=(_line+"\r\n").getBytes();
                else if (_line.length()==0)
                    _buffer="\r\n".getBytes();
                else
                    _buffer=B64Code.decode(_line);

                _pos=0;
            }
            return _buffer[_pos++];
        }
    }
}
