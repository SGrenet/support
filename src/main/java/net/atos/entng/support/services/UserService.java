package net.atos.entng.support.services;

import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

import fr.wseduc.webutils.Either;

public interface UserService {

	public void getLocalAdministrators(final UserInfos user, final String structure,
			final Handler<Either<String, JsonArray>> handler);

}
