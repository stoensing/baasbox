/*
     Copyright 2012-2013 
     Claudio Tesoriero - c.tesoriero-at-baasbox.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package com.baasbox.dao;

import java.security.InvalidParameterException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import play.Logger;

import com.baasbox.dao.exception.UserAlreadyExistsException;
import com.baasbox.db.DbHelper;
import com.baasbox.enumerations.DefaultRoles;
import com.baasbox.exception.SqlInjectionException;
import com.baasbox.exception.UserNotFoundException;
import com.baasbox.service.sociallogin.UserInfo;
import com.baasbox.util.QueryParams;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.OUser;
import com.orientechnologies.orient.core.metadata.security.OUser.STATUSES;
import com.orientechnologies.orient.core.record.impl.ODocument;






public class UserDao extends NodeDao  {

	public final static String MODEL_NAME="_BB_User";
	private final static String USER_LINK = "user";
	private final static String USER_NAME_INDEX = "ouser.name";

	public final static String USER_DEVICE_ID="deviceId";
	public final static String USER_DEVICE_OS="os";
	public final static String USER_LOGIN_INFO="login_info";
	public final static String SOCIAL_LOGIN_INFO="sso_tokens";

	public final static String USER_ATTRIBUTES_CLASS = "_BB_UserAttributes";
	public final static String ATTRIBUTES_VISIBLE_BY_ANONYMOUS_USER="visibleByAnonymousUsers";
	public final static String ATTRIBUTES_VISIBLE_BY_REGISTERED_USER="visibleByRegisteredUsers";
	public final static String ATTRIBUTES_VISIBLE_BY_FRIENDS_USER="visibleByFriend";
	public final static String ATTRIBUTES_VISIBLE_ONLY_BY_THE_USER="visibleByTheUser";
	public final static String USER_SIGNUP_DATE="signUpDate";
	public final static String ATTRIBUTES_SYSTEM="system";
	public static final String GENERATED_USERNAME = "generated_username";

	public static UserDao getInstance(){
		return new UserDao();
	}

	protected UserDao() {
		super(MODEL_NAME);
	}

	@Override
	@Deprecated
	public ODocument create(){
		throw new IllegalAccessError("To create a new user call create(String username, String password) or create(String username, String password, String role)");
	}

	public ODocument create(String username, String password) throws UserAlreadyExistsException {
		return create(username, password, null);
	};

	public ODocument create(String username, String password, String role) throws UserAlreadyExistsException,InvalidParameterException {
		if (existsUserName(username)) throw new UserAlreadyExistsException("User " + username + " already exists");
		OUser user=null;
		if (role==null) user=db.getMetadata().getSecurity().createUser(username,password,new 
				String[]{DefaultRoles.REGISTERED_USER.toString()}); 
		else {
			ORole orole = RoleDao.getRole(role);
			if (orole==null) throw new InvalidParameterException("Role " + role + " does not exists");
			user=db.getMetadata().getSecurity().createUser(username,password,new String[]{role}); 
		}
		ODocument doc = new ODocument(this.MODEL_NAME);
		ODocument vertex = db.createVertex(CLASS_VERTEX_NAME);
		doc.field(FIELD_LINK_TO_VERTEX,vertex);
		doc.field(FIELD_CREATION_DATE,new Date());
		vertex.field(FIELD_TO_DOCUMENT_FIELD,doc);

		doc.field(USER_LINK,user.getDocument().getIdentity());
		doc.save();
		return doc;
	}

	public boolean existsUserName(String username){
		OIndex idx = db.getMetadata().getIndexManager().getIndex(USER_NAME_INDEX);
		OIdentifiable record = (OIdentifiable) idx.get( username );
		return (record!=null);
	}

	public ODocument getByUserName(String username) throws SqlInjectionException{
		ODocument result=null;
		QueryParams criteria = QueryParams.getInstance().where("user.name=?").params(new String [] {username});
		List<ODocument> resultList= super.get(criteria);
		if (resultList!=null && resultList.size()>0) result = resultList.get(0);
		return result;
	}

	public ODocument getBySocialUserId(UserInfo ui) throws SqlInjectionException{
		ODocument result=null;
		StringBuffer where = new StringBuffer(UserDao.ATTRIBUTES_SYSTEM).append(".");
		where.append(UserDao.SOCIAL_LOGIN_INFO).append("[").append(ui.getFrom()).append("]").append(".id").append(" = ?");
		QueryParams criteria = QueryParams.getInstance().where(where.toString()).params(new String [] {ui.getId()});
		List<ODocument> resultList= super.get(criteria);
		Logger.debug("Found "+resultList.size() +" elements for given tokens");
		if (resultList!=null && resultList.size()>0) result = resultList.get(0);

		return result;
	}

	public void disableUser(String username) throws UserNotFoundException{
		db = DbHelper.reconnectAsAdmin();
		OUser user = db.getMetadata().getSecurity().getUser(username);
		if (user==null) throw new UserNotFoundException("The user " + username + " does not exist.");
		user.setAccountStatus(STATUSES.SUSPENDED);
		user.save();
		//cannot resume the old connection because now the user is disabled
	}
	
	public void enableUser(String username) throws UserNotFoundException{
		db = DbHelper.reconnectAsAdmin();
		OUser user = db.getMetadata().getSecurity().getUser(username);
		if (user==null) throw new UserNotFoundException("The user " + username + " does not exist.");
		user.setAccountStatus(STATUSES.ACTIVE);
		user.save();
	}

}
