//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
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


package org.eclipse.jetty.server.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.JDBCSessionIdManager.SessionTableSchema;
import org.eclipse.jetty.util.ClassLoadingObjectInputStream;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * JDBCSessionManager
 *
 * SessionManager that persists sessions to a database to enable clustering.
 *
 * Session data is persisted to the JettySessions table:
 *
 * rowId (unique in cluster: webapp name/path + virtualhost + sessionId)
 * contextPath (of the context owning the session)
 * sessionId (unique in a context)
 * lastNode (name of node last handled session)
 * accessTime (time in milliseconds session was accessed)
 * lastAccessTime (previous time in milliseconds session was accessed)
 * createTime (time in milliseconds session created)
 * cookieTime (time in milliseconds session cookie created)
 * lastSavedTime (last time in milliseconds session access times were saved)
 * expiryTime (time in milliseconds that the session is due to expire)
 * map (attribute map)
 *
 * As an optimization, to prevent thrashing the database, we do not persist
 * the accessTime and lastAccessTime every time the session is accessed. Rather,
 * we write it out every so often. The frequency is controlled by the saveIntervalSec
 * field.
 */
public class JDBCSessionManager extends AbstractSessionManager
{
    private static final Logger LOG = Log.getLogger(JDBCSessionManager.class);

    private ConcurrentHashMap<String, Session> _sessions;
    protected JDBCSessionIdManager _jdbcSessionIdMgr = null;
    protected long _saveIntervalSec = 60; //only persist changes to session access times every 60 secs
    protected SessionTableSchema _sessionTableSchema;

   


    /**
     * Session
     *
     * Session instance.
     */
    public class Session extends MemSession
    {
        private static final long serialVersionUID = 5208464051134226143L;
        
        /**
         * If dirty, session needs to be (re)persisted
         */
        protected boolean _dirty=false;
        
        
        /**
         * Time in msec since the epoch that a session cookie was set for this session
         */
        protected long _cookieSet;
        
        
        /**
         * Time in msec since the epoch that the session will expire
         */
        protected long _expiryTime;
        
        
        /**
         * Time in msec since the epoch that the session was last persisted
         */
        protected long _lastSaved;
        
        
        /**
         * Unique identifier of the last node to host the session
         */
        protected String _lastNode;
        
        
        /**
         * Virtual host for context (used to help distinguish 2 sessions with same id on different contexts)
         */
        protected String _virtualHost;
        
        
        /**
         * Unique row in db for session
         */
        protected String _rowId;
        
        
        /**
         * Mangled context name (used to help distinguish 2 sessions with same id on different contexts)
         */
        protected String _canonicalContext;
        
   
        /**
         * Session from a request.
         *
         * @param request
         */
        protected Session (HttpServletRequest request)
        {
            super(JDBCSessionManager.this,request);
            int maxInterval=getMaxInactiveInterval();
            _expiryTime = (maxInterval <= 0 ? 0 : (System.currentTimeMillis() + maxInterval*1000L));
            _virtualHost = JDBCSessionManager.getVirtualHost(_context);
            _canonicalContext = canonicalize(_context.getContextPath());
            _lastNode = getSessionIdManager().getWorkerName();
        }
        
        
        /**
         * Session restored from database
         * @param sessionId
         * @param rowId
         * @param created
         * @param accessed
         */
        protected Session (String sessionId, String rowId, long created, long accessed, long maxInterval)
        {
            super(JDBCSessionManager.this, created, accessed, sessionId);
            _rowId = rowId;
            super.setMaxInactiveInterval((int)maxInterval); //restore the session's previous inactivity interval setting
            _expiryTime = (maxInterval <= 0 ? 0 : (System.currentTimeMillis() + maxInterval*1000L));
        }
        
        
        protected synchronized String getRowId()
        {
            return _rowId;
        }
        
        protected synchronized void setRowId(String rowId)
        {
            _rowId = rowId;
        }
        
        public synchronized void setVirtualHost (String vhost)
        {
            _virtualHost=vhost;
        }

        public synchronized String getVirtualHost ()
        {
            return _virtualHost;
        }
        
        public synchronized long getLastSaved ()
        {
            return _lastSaved;
        }

        public synchronized void setLastSaved (long time)
        {
            _lastSaved=time;
        }

        public synchronized void setExpiryTime (long time)
        {
            _expiryTime=time;
        }

        public synchronized long getExpiryTime ()
        {
            return _expiryTime;
        }
        

        public synchronized void setCanonicalContext(String str)
        {
            _canonicalContext=str;
        }

        public synchronized String getCanonicalContext ()
        {
            return _canonicalContext;
        }
        
        public void setCookieSet (long ms)
        {
            _cookieSet = ms;
        }

        public synchronized long getCookieSet ()
        {
            return _cookieSet;
        }

        public synchronized void setLastNode (String node)
        {
            _lastNode=node;
        }

        public synchronized String getLastNode ()
        {
            return _lastNode;
        }

        @Override
        public void setAttribute (String name, Object value)
        {
            Object old = changeAttribute(name, value);
            if (value == null && old == null)
                return; //if same as remove attribute but attribute was already removed, no change
            
            _dirty = true;
        }

        @Override
        public void removeAttribute (String name)
        {
            Object old = changeAttribute(name, null);
            if (old != null) //only dirty if there was a previous value
                _dirty=true;
        }

        @Override
        protected void cookieSet()
        {
            _cookieSet = getAccessed();
        }

        /**
         * Entry to session.
         * Called by SessionHandler on inbound request and the session already exists in this node's memory.
         *
         * @see org.eclipse.jetty.server.session.AbstractSession#access(long)
         */
        @Override
        protected boolean access(long time)
        {
            synchronized (this)
            {
                if (super.access(time))
                {
                    int maxInterval=getMaxInactiveInterval();
                    _expiryTime = (maxInterval <= 0 ? 0 : (time + maxInterval*1000L));
                    return true;
                }
                return false;
            }
        }
        
        
        


        /** 
         * Change the max idle time for this session. This recalculates the expiry time.
         * @see org.eclipse.jetty.server.session.AbstractSession#setMaxInactiveInterval(int)
         */
        @Override
        public void setMaxInactiveInterval(int secs)
        {
            synchronized (this)
            {
                super.setMaxInactiveInterval(secs);
                int maxInterval=getMaxInactiveInterval();
                _expiryTime = (maxInterval <= 0 ? 0 : (System.currentTimeMillis() + maxInterval*1000L));
                //force the session to be written out right now
                try
                {
                    updateSessionAccessTime(this);
                }
                catch (Exception e)
                {
                    LOG.warn("Problem saving changed max idle time for session "+ this, e);
                }
            }
        }


        /**
         * Exit from session
         * @see org.eclipse.jetty.server.session.AbstractSession#complete()
         */
        @Override
        protected void complete()
        {
            synchronized (this)
            {
                super.complete();
                try
                {
                    if (isValid())
                    {
                        if (_dirty)
                        {
                            //The session attributes have changed, write to the db, ensuring
                            //http passivation/activation listeners called
                            save(true);
                        }
                        else if ((getAccessed() - _lastSaved) >= (getSaveInterval() * 1000L))
                        {
                            updateSessionAccessTime(this);
                        }
                    }
                }
                catch (Exception e)
                {
                    LOG.warn("Problem persisting changed session data id="+getId(), e);
                }
                finally
                {
                    _dirty=false;
                }
            }
        }

        protected void save() throws Exception
        {
            synchronized (this)
            {
                try
                {
                    updateSession(this);
                }
                finally
                {
                    _dirty = false;
                }
            }
        }

        protected void save (boolean reactivate) throws Exception
        {
            synchronized (this)
            {
                if (_dirty)
                {
                    //The session attributes have changed, write to the db, ensuring
                    //http passivation/activation listeners called
                    willPassivate();                      
                    updateSession(this);
                    if (reactivate)
                        didActivate();  
                }
            }
        }

        
        @Override
        protected void timeout() throws IllegalStateException
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Timing out session id="+getClusterId());
            super.timeout();
        }
        
        
        @Override
        public String toString ()
        {
            return "Session rowId="+_rowId+",id="+getId()+",lastNode="+_lastNode+
                            ",created="+getCreationTime()+",accessed="+getAccessed()+
                            ",lastAccessed="+getLastAccessedTime()+",cookieSet="+_cookieSet+
                            ",maxInterval="+getMaxInactiveInterval()+",lastSaved="+_lastSaved+",expiry="+_expiryTime;
        }
    }




    /**
     * Set the time in seconds which is the interval between
     * saving the session access time to the database.
     *
     * This is an optimization that prevents the database from
     * being overloaded when a session is accessed very frequently.
     *
     * On session exit, if the session attributes have NOT changed,
     * the time at which we last saved the accessed
     * time is compared to the current accessed time. If the interval
     * is at least saveIntervalSecs, then the access time will be
     * persisted to the database.
     *
     * If any session attribute does change, then the attributes and
     * the accessed time are persisted.
     *
     * @param sec
     */
    public void setSaveInterval (long sec)
    {
        _saveIntervalSec=sec;
    }

    public long getSaveInterval ()
    {
        return _saveIntervalSec;
    }



    /**
     * A method that can be implemented in subclasses to support
     * distributed caching of sessions. This method will be
     * called whenever the session is written to the database
     * because the session data has changed.
     *
     * This could be used eg with a JMS backplane to notify nodes
     * that the session has changed and to delete the session from
     * the node's cache, and re-read it from the database.
     * @param session
     */
    public void cacheInvalidate (Session session)
    {

    }


    /**
     * A session has been requested by its id on this node.
     *
     * Load the session by id AND context path from the database.
     * Multiple contexts may share the same session id (due to dispatching)
     * but they CANNOT share the same contents.
     *
     * Check if last node id is my node id, if so, then the session we have
     * in memory cannot be stale. If another node used the session last, then
     * we need to refresh from the db.
     *
     * NOTE: this method will go to the database, so if you only want to check
     * for the existence of a Session in memory, use _sessions.get(id) instead.
     *
     * @see org.eclipse.jetty.server.session.AbstractSessionManager#getSession(java.lang.String)
     */
    @Override
    public Session getSession(String idInCluster)
    {
        Session session = null;
        
        synchronized (this)
        {
            Session memSession = (Session)_sessions.get(idInCluster);
            
            //check if we need to reload the session -
            //as an optimization, don't reload on every access
            //to reduce the load on the database. This introduces a window of
            //possibility that the node may decide that the session is local to it,
            //when the session has actually been live on another node, and then
            //re-migrated to this node. This should be an extremely rare occurrence,
            //as load-balancers are generally well-behaved and consistently send
            //sessions to the same node, changing only iff that node fails.
            //Session data = null;
            long now = System.currentTimeMillis();
            if (LOG.isDebugEnabled())
            {
                if (memSession==null)
                    LOG.debug("getSession("+idInCluster+"): not in session map,"+
                            " now="+now+
                            " lastSaved="+(memSession==null?0:memSession._lastSaved)+
                            " interval="+(_saveIntervalSec * 1000L));
                else
                    LOG.debug("getSession("+idInCluster+"): in session map, "+
                            " hashcode="+memSession.hashCode()+
                            " now="+now+
                            " lastSaved="+(memSession==null?0:memSession._lastSaved)+
                            " interval="+(_saveIntervalSec * 1000L)+
                            " lastNode="+memSession._lastNode+
                            " thisNode="+getSessionIdManager().getWorkerName()+
                            " difference="+(now - memSession._lastSaved));
            }

            try
            {
                if (memSession==null)
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("getSession("+idInCluster+"): no session in session map. Reloading session data from db.");
                    session = loadSession(idInCluster, canonicalize(_context.getContextPath()), getVirtualHost(_context));
                }
                else if ((now - memSession._lastSaved) >= (_saveIntervalSec * 1000L))
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("getSession("+idInCluster+"): stale session. Reloading session data from db.");
                    session = loadSession(idInCluster, canonicalize(_context.getContextPath()), getVirtualHost(_context));
                }
                else
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("getSession("+idInCluster+"): session in session map");
                    session = memSession;
                }
            }
            catch (Exception e)
            {
                LOG.warn("Unable to load session "+idInCluster, e);
                return null;
            }


            //If we have a session
            if (session != null)
            {
                //If the session was last used on a different node, or session doesn't exist on this node
                if (!session.getLastNode().equals(getSessionIdManager().getWorkerName()) || memSession==null)
                {
                    //if session doesn't expire, or has not already expired, update it and put it in this nodes' memory
                    if (session._expiryTime <= 0 || session._expiryTime > now)
                    {
                        if (LOG.isDebugEnabled()) 
                            LOG.debug("getSession("+idInCluster+"): lastNode="+session.getLastNode()+" thisNode="+getSessionIdManager().getWorkerName());

                        session.setLastNode(getSessionIdManager().getWorkerName());                            
                        _sessions.put(idInCluster, session);

                        //update in db
                        try
                        {
                            updateSessionNode(session);
                            session.didActivate();
                        }
                        catch (Exception e)
                        {
                            LOG.warn("Unable to update freshly loaded session "+idInCluster, e);
                            return null;
                        }
                    }
                    else
                    {
                        if (LOG.isDebugEnabled())
                            LOG.debug("getSession ({}): Session has expired", idInCluster);
                        //ensure that the session id for the expired session is deleted so that a new session with the 
                        //same id cannot be created (because the idInUse() test would succeed)
                        _jdbcSessionIdMgr.removeSession(idInCluster);
                        session=null;
                    }

                }
                else
                {
                    //the session loaded from the db and the one in memory are the same, so keep using the one in memory
                    session = memSession;
                    if (LOG.isDebugEnabled())
                        LOG.debug("getSession({}): Session not stale {}", idInCluster,session);
                }
            }
            else
            {
                //No session in db with matching id and context path.
                LOG.debug("getSession({}): No session in database matching id={}",idInCluster,idInCluster);
            }

            return session;
        }
    }
    

    /**
     * Get the number of sessions.
     *
     * @see org.eclipse.jetty.server.session.AbstractSessionManager#getSessions()
     */
    @Override
    public int getSessions()
    {
        return _sessions.size();
    }


    /**
     * Start the session manager.
     *
     * @see org.eclipse.jetty.server.session.AbstractSessionManager#doStart()
     */
    @Override
    public void doStart() throws Exception
    {
        if (_sessionIdManager==null)
            throw new IllegalStateException("No session id manager defined");

        _jdbcSessionIdMgr = (JDBCSessionIdManager)_sessionIdManager;
        _sessionTableSchema = _jdbcSessionIdMgr.getSessionTableSchema();

        _sessions = new ConcurrentHashMap<String, Session>();

        super.doStart();
    }


    /**
     * Stop the session manager.
     *
     * @see org.eclipse.jetty.server.session.AbstractSessionManager#doStop()
     */
    @Override
    public void doStop() throws Exception
    {
        super.doStop();
        _sessions.clear();
        _sessions = null;
    }

    @Override
    protected void shutdownSessions()
    {
        //Save the current state of all of our sessions,
        //do NOT delete them (so other nodes can manage them)
        long gracefulStopMs = getContextHandler().getServer().getStopTimeout();
        long stopTime = 0;
        if (gracefulStopMs > 0)
            stopTime = System.nanoTime() + (TimeUnit.NANOSECONDS.convert(gracefulStopMs, TimeUnit.MILLISECONDS));        

        ArrayList<Session> sessions = (_sessions == null? new ArrayList<Session>() :new ArrayList<Session>(_sessions.values()) );

        // loop while there are sessions, and while there is stop time remaining, or if no stop time, just 1 loop
        while (sessions.size() > 0 && ((stopTime > 0 && (System.nanoTime() < stopTime)) || (stopTime == 0)))
        {
            for (Session session : sessions)
            {
                try
                {
                    session.save(false);
                }
                catch (Exception e)
                {
                    LOG.warn(e);
                }
                _sessions.remove(session.getClusterId());
            }

            //check if we should terminate our loop if we're not using the stop timer
            if (stopTime == 0)
                break;
            
            // Get any sessions that were added by other requests during processing and go around the loop again
            sessions=new ArrayList<Session>(_sessions.values());
        }
    }

    
    /**
     * 
     * @see org.eclipse.jetty.server.SessionManager#renewSessionId(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public void renewSessionId (String oldClusterId, String oldNodeId, String newClusterId, String newNodeId)
    {
        Session session = null;
        try
        {
            session = (Session)_sessions.remove(oldClusterId);
            if (session != null)
            {
                synchronized (session)
                {
                    session.setClusterId(newClusterId); //update ids
                    session.setNodeId(newNodeId);
                    _sessions.put(newClusterId, session); //put it into list in memory
                    updateSession(session); //update database
                }
            }
        }
        catch (Exception e)
        {
            LOG.warn(e);
        }

        super.renewSessionId(oldClusterId, oldNodeId, newClusterId, newNodeId);
    }

    

    /**
     * Invalidate a session.
     *
     * @param idInCluster
     */
    protected void invalidateSession (String idInCluster)
    {
        Session session = (Session)_sessions.get(idInCluster);

        if (session != null)
        {
            session.invalidate();
        }
    }

    /**
     * Delete an existing session, both from the in-memory map and
     * the database.
     *
     * @see org.eclipse.jetty.server.session.AbstractSessionManager#removeSession(java.lang.String)
     */
    @Override
    protected boolean removeSession(String idInCluster)
    {
        Session session = (Session)_sessions.remove(idInCluster);
        try
        {
            if (session != null)
                deleteSession(session);
        }
        catch (Exception e)
        {
            LOG.warn("Problem deleting session id="+idInCluster, e);
        }
        return session!=null;
    }


    /**
     * Add a newly created session to our in-memory list for this node and persist it.
     *
     * @see org.eclipse.jetty.server.session.AbstractSessionManager#addSession(org.eclipse.jetty.server.session.AbstractSession)
     */
    @Override
    protected void addSession(AbstractSession session)
    {
        if (session==null)
            return;

        _sessions.put(session.getClusterId(), (Session)session);

        try
        {
            synchronized (session)
            {
                session.willPassivate();
                storeSession(((JDBCSessionManager.Session)session));
                session.didActivate();
            }
        }
        catch (Exception e)
        {
            LOG.warn("Unable to store new session id="+session.getId() , e);
        }
    }


    /**
     * Make a new Session.
     *
     * @see org.eclipse.jetty.server.session.AbstractSessionManager#newSession(javax.servlet.http.HttpServletRequest)
     */
    @Override
    protected AbstractSession newSession(HttpServletRequest request)
    {
        return new Session(request);
    }
    
    
    /**
     * @param sessionId
     * @param rowId
     * @param created
     * @param accessed
     * @param maxInterval
     * @return
     */
    protected AbstractSession newSession (String sessionId, String rowId, long created, long accessed, long maxInterval)
    {
        return new Session(sessionId, rowId, created, accessed, maxInterval);
    }

    /* ------------------------------------------------------------ */
    /** Remove session from manager
     * @param session The session to remove
     * @param invalidate True if {@link HttpSessionListener#sessionDestroyed(HttpSessionEvent)} and
     * {@link SessionIdManager#invalidateAll(String)} should be called.
     */
    @Override
    public boolean removeSession(AbstractSession session, boolean invalidate)
    {
        // Remove session from context and global maps
        boolean removed = super.removeSession(session, invalidate);

        if (removed)
        {
            if (!invalidate)
            {
                session.willPassivate();
            }
        }
        
        return removed;
    }


    /**
     * Expire any Sessions we have in memory matching the list of
     * expired Session ids.
     *
     * @param sessionIds
     */
    protected Set<String> expire (Set<String> sessionIds)
    {
        //don't attempt to scavenge if we are shutting down
        if (isStopping() || isStopped())
            return null;

        
        Thread thread=Thread.currentThread();
        ClassLoader old_loader=thread.getContextClassLoader();
        
        Set<String> successfullyExpiredIds = new HashSet<String>();
        try
        {
            Iterator<?> itor = sessionIds.iterator();
            while (itor.hasNext())
            {
                String sessionId = (String)itor.next();
                if (LOG.isDebugEnabled())
                    LOG.debug("Expiring session id "+sessionId);

                Session session = (Session)_sessions.get(sessionId);

                //if session is not in our memory, then fetch from db so we can call the usual listeners on it
                if (session == null)
                {
                    if (LOG.isDebugEnabled())LOG.debug("Force loading session id "+sessionId);
                    session = loadSession(sessionId, canonicalize(_context.getContextPath()), getVirtualHost(_context));
                    if (session != null)
                    {
                        //loaded an expired session last managed on this node for this context, add it to the list so we can 
                        //treat it like a normal expired session
                        _sessions.put(session.getClusterId(), session);
                    }
                    else
                    {
                        if (LOG.isDebugEnabled())
                            LOG.debug("Unrecognized session id="+sessionId);
                        continue;
                    }
                }

                if (session != null)
                {
                    session.timeout();
                    successfullyExpiredIds.add(session.getClusterId());
                }
            }
            return successfullyExpiredIds;
        }
        catch (Throwable t)
        {
            LOG.warn("Problem expiring sessions", t);
            return successfullyExpiredIds;
        }
        finally
        {
            thread.setContextClassLoader(old_loader);
        }
    }
    
  
    /**
     * Load a session from the database
     * @param id
     * @return the session data that was loaded
     * @throws Exception
     */
    protected Session loadSession (final String id, final String canonicalContextPath, final String vhost)
    throws Exception
    {
        final AtomicReference<Session> _reference = new AtomicReference<Session>();
        final AtomicReference<Exception> _exception = new AtomicReference<Exception>();
        Runnable load = new Runnable()
        {
            /** 
             * @see java.lang.Runnable#run()
             */
            @SuppressWarnings("unchecked")
            public void run()
            {
                try (Connection connection = getConnection();
                        PreparedStatement statement = _sessionTableSchema.getLoadStatement(connection, id, canonicalContextPath, vhost);
                        ResultSet result = statement.executeQuery())
                {
                    Session session = null;
                    if (result.next())
                    {                    
                        long maxInterval = result.getLong(_sessionTableSchema.getMaxIntervalColumn());
                        if (maxInterval == JDBCSessionIdManager.MAX_INTERVAL_NOT_SET)
                        {
                            maxInterval = getMaxInactiveInterval(); //if value not saved for maxInactiveInterval, use current value from sessionmanager
                        }
                        session = (Session)newSession(id, result.getString(_sessionTableSchema.getRowIdColumn()), 
                                                  result.getLong(_sessionTableSchema.getCreateTimeColumn()), 
                                                  result.getLong(_sessionTableSchema.getAccessTimeColumn()), 
                                                  maxInterval);
                        session.setCookieSet(result.getLong(_sessionTableSchema.getCookieTimeColumn()));
                        session.setLastAccessedTime(result.getLong(_sessionTableSchema.getLastAccessTimeColumn()));
                        session.setLastNode(result.getString(_sessionTableSchema.getLastNodeColumn()));
                        session.setLastSaved(result.getLong(_sessionTableSchema.getLastSavedTimeColumn()));
                        session.setExpiryTime(result.getLong(_sessionTableSchema.getExpiryTimeColumn()));
                        session.setCanonicalContext(result.getString(_sessionTableSchema.getContextPathColumn()));
                        session.setVirtualHost(result.getString(_sessionTableSchema.getVirtualHostColumn()));
                                           
                        try (InputStream is = ((JDBCSessionIdManager)getSessionIdManager())._dbAdaptor.getBlobInputStream(result, _sessionTableSchema.getMapColumn());
                                ClassLoadingObjectInputStream ois = new ClassLoadingObjectInputStream(is))
                        {
                            Object o = ois.readObject();
                            session.addAttributes((Map<String,Object>)o);
                        }

                        if (LOG.isDebugEnabled())
                            LOG.debug("LOADED session "+session);
                    }
                    else
                        if (LOG.isDebugEnabled())
                            LOG.debug("Failed to load session "+id);
                    _reference.set(session);
                }
                catch (Exception e)
                {
                    _exception.set(e);
                }
            }
        };

        if (_context==null)
            load.run();
        else
            _context.getContextHandler().handle(load);

        if (_exception.get()!=null)
        {
            //if the session could not be restored, take its id out of the pool of currently-in-use
            //session ids
            _jdbcSessionIdMgr.removeSession(id);
            throw _exception.get();
        }

        return _reference.get();
    }

    /**
     * Insert a session into the database.
     *
     * @param session
     * @throws Exception
     */
    protected void storeSession (Session session)
    throws Exception
    {
        if (session==null)
            return;

        //put into the database
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(_jdbcSessionIdMgr._insertSession))
        {
            String rowId = calculateRowId(session);

            long now = System.currentTimeMillis();
            connection.setAutoCommit(true);
            statement.setString(1, rowId); //rowId
            statement.setString(2, session.getClusterId()); //session id
            statement.setString(3, session.getCanonicalContext()); //context path
            statement.setString(4, session.getVirtualHost()); //first vhost
            statement.setString(5, getSessionIdManager().getWorkerName());//my node id
            statement.setLong(6, session.getAccessed());//accessTime
            statement.setLong(7, session.getLastAccessedTime()); //lastAccessTime
            statement.setLong(8, session.getCreationTime()); //time created
            statement.setLong(9, session.getCookieSet());//time cookie was set
            statement.setLong(10, now); //last saved time
            statement.setLong(11, session.getExpiryTime());
            statement.setLong(12, session.getMaxInactiveInterval());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(session.getAttributeMap());
            oos.flush();
            byte[] bytes = baos.toByteArray();

            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            statement.setBinaryStream(13, bais, bytes.length);//attribute map as blob
           

            statement.executeUpdate();
            session.setRowId(rowId); //set it on the in-memory data as well as in db
            session.setLastSaved(now);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Stored session "+session);
    }


    /**
     * Update data on an existing persisted session.
     *
     * @param data the session
     * @throws Exception
     */
    protected void updateSession (Session data)
    throws Exception
    {
        if (data==null)
            return;

        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(_jdbcSessionIdMgr._updateSession))
        {
            long now = System.currentTimeMillis();
            connection.setAutoCommit(true);
            statement.setString(1, data.getClusterId());
            statement.setString(2, getSessionIdManager().getWorkerName());//my node id
            statement.setLong(3, data.getAccessed());//accessTime
            statement.setLong(4, data.getLastAccessedTime()); //lastAccessTime
            statement.setLong(5, now); //last saved time
            statement.setLong(6, data.getExpiryTime());
            statement.setLong(7, data.getMaxInactiveInterval());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(data.getAttributeMap());
            oos.flush();
            byte[] bytes = baos.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

            statement.setBinaryStream(8, bais, bytes.length);//attribute map as blob
            statement.setString(9, data.getRowId()); //rowId
            statement.executeUpdate();

            data.setLastSaved(now);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Updated session "+data);
    }


    /**
     * Update the node on which the session was last seen to be my node.
     *
     * @param data the session
     * @throws Exception
     */
    protected void updateSessionNode (Session data)
    throws Exception
    {
        String nodeId = getSessionIdManager().getWorkerName();
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(_jdbcSessionIdMgr._updateSessionNode))
        {
            connection.setAutoCommit(true);
            statement.setString(1, nodeId);
            statement.setString(2, data.getRowId());
            statement.executeUpdate();
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Updated last node for session id="+data.getId()+", lastNode = "+nodeId);
    }

    /**
     * Persist the time the session was last accessed.
     *
     * @param data the session
     * @throws Exception
     */
    private void updateSessionAccessTime (Session data)
    throws Exception
    {
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(_jdbcSessionIdMgr._updateSessionAccessTime))
        {
            long now = System.currentTimeMillis();
            connection.setAutoCommit(true);
            statement.setString(1, getSessionIdManager().getWorkerName());
            statement.setLong(2, data.getAccessed());
            statement.setLong(3, data.getLastAccessedTime());
            statement.setLong(4, now);
            statement.setLong(5, data.getExpiryTime());
            statement.setLong(6, data.getMaxInactiveInterval());
            statement.setString(7, data.getRowId());
          
            statement.executeUpdate();
            data.setLastSaved(now);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Updated access time session id="+data.getId()+" with lastsaved="+data.getLastSaved());
    }




    /**
     * Delete a session from the database. Should only be called
     * when the session has been invalidated.
     *
     * @param data
     * @throws Exception
     */
    protected void deleteSession (Session data)
    throws Exception
    {
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(_jdbcSessionIdMgr._deleteSession))
        {
            connection.setAutoCommit(true);
            statement.setString(1, data.getRowId());
            statement.executeUpdate();
            if (LOG.isDebugEnabled())
                LOG.debug("Deleted Session "+data);
        }
    }



    /**
     * Get a connection from the driver.
     * @return
     * @throws SQLException
     */
    private Connection getConnection ()
    throws SQLException
    {
        return ((JDBCSessionIdManager)getSessionIdManager()).getConnection();
    }

    /**
     * Calculate a unique id for this session across the cluster.
     *
     * Unique id is composed of: contextpath_virtualhost0_sessionid
     * @param data
     * @return
     */
    private String calculateRowId (Session data)
    {
        String rowId = canonicalize(_context.getContextPath());
        rowId = rowId + "_" + getVirtualHost(_context);
        rowId = rowId+"_"+data.getId();
        return rowId;
    }

    /**
     * Get the first virtual host for the context.
     *
     * Used to help identify the exact session/contextPath.
     *
     * @return 0.0.0.0 if no virtual host is defined
     */
    private static String getVirtualHost (ContextHandler.Context context)
    {
        String vhost = "0.0.0.0";

        if (context==null)
            return vhost;

        String [] vhosts = context.getContextHandler().getVirtualHosts();
        if (vhosts==null || vhosts.length==0 || vhosts[0]==null)
            return vhost;

        return vhosts[0];
    }

    /**
     * Make an acceptable file name from a context path.
     *
     * @param path
     * @return
     */
    private static String canonicalize (String path)
    {
        if (path==null)
            return "";

        return path.replace('/', '_').replace('.','_').replace('\\','_');
    }
}
