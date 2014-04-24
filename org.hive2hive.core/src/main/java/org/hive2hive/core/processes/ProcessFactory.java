package org.hive2hive.core.processes;

import java.io.File;
import java.security.PublicKey;
import java.util.List;
import java.util.Set;

import org.hive2hive.core.H2HSession;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.model.UserPermission;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.network.data.DataManager;
import org.hive2hive.core.processes.framework.abstracts.ProcessComponent;
import org.hive2hive.core.processes.framework.concretes.SequentialProcess;
import org.hive2hive.core.processes.framework.decorators.AsyncComponent;
import org.hive2hive.core.processes.framework.decorators.AsyncResultComponent;
import org.hive2hive.core.processes.framework.interfaces.IResultProcessComponent;
import org.hive2hive.core.processes.implementations.common.File2MetaFileComponent;
import org.hive2hive.core.processes.implementations.common.GetUserLocationsStep;
import org.hive2hive.core.processes.implementations.common.GetUserProfileStep;
import org.hive2hive.core.processes.implementations.common.PutMetaFileStep;
import org.hive2hive.core.processes.implementations.common.PutUserLocationsStep;
import org.hive2hive.core.processes.implementations.common.userprofiletask.GetUserProfileTaskStep;
import org.hive2hive.core.processes.implementations.context.AddFileProcessContext;
import org.hive2hive.core.processes.implementations.context.DeleteFileProcessContext;
import org.hive2hive.core.processes.implementations.context.DownloadFileContext;
import org.hive2hive.core.processes.implementations.context.LoginProcessContext;
import org.hive2hive.core.processes.implementations.context.LogoutProcessContext;
import org.hive2hive.core.processes.implementations.context.MoveFileProcessContext;
import org.hive2hive.core.processes.implementations.context.NotifyProcessContext;
import org.hive2hive.core.processes.implementations.context.RecoverFileContext;
import org.hive2hive.core.processes.implementations.context.RegisterProcessContext;
import org.hive2hive.core.processes.implementations.context.ShareProcessContext;
import org.hive2hive.core.processes.implementations.context.UpdateFileProcessContext;
import org.hive2hive.core.processes.implementations.context.UserProfileTaskContext;
import org.hive2hive.core.processes.implementations.context.interfaces.IConsumeNotificationFactory;
import org.hive2hive.core.processes.implementations.files.add.AddIndexToUserProfileStep;
import org.hive2hive.core.processes.implementations.files.add.CheckWriteAccessStep;
import org.hive2hive.core.processes.implementations.files.add.CreateMetaFileStep;
import org.hive2hive.core.processes.implementations.files.add.InitializeChunksStep;
import org.hive2hive.core.processes.implementations.files.add.PrepareNotificationStep;
import org.hive2hive.core.processes.implementations.files.add.ValidateFileSizeStep;
import org.hive2hive.core.processes.implementations.files.delete.DeleteFileOnDiskStep;
import org.hive2hive.core.processes.implementations.files.delete.DeleteFromUserProfileStep;
import org.hive2hive.core.processes.implementations.files.delete.PrepareDeleteNotificationStep;
import org.hive2hive.core.processes.implementations.files.download.FindInUserProfileStep;
import org.hive2hive.core.processes.implementations.files.list.FileTaste;
import org.hive2hive.core.processes.implementations.files.list.GetFileListStep;
import org.hive2hive.core.processes.implementations.files.move.MoveOnDiskStep;
import org.hive2hive.core.processes.implementations.files.move.RelinkUserProfileStep;
import org.hive2hive.core.processes.implementations.files.recover.IVersionSelector;
import org.hive2hive.core.processes.implementations.files.recover.SelectVersionStep;
import org.hive2hive.core.processes.implementations.files.update.CleanupChunksStep;
import org.hive2hive.core.processes.implementations.files.update.CreateNewVersionStep;
import org.hive2hive.core.processes.implementations.files.update.UpdateMD5inUserProfileStep;
import org.hive2hive.core.processes.implementations.login.ContactOtherClientsStep;
import org.hive2hive.core.processes.implementations.login.SessionCreationStep;
import org.hive2hive.core.processes.implementations.login.SessionParameters;
import org.hive2hive.core.processes.implementations.login.SynchronizeFilesStep;
import org.hive2hive.core.processes.implementations.logout.DeleteSessionStep;
import org.hive2hive.core.processes.implementations.logout.RemoveOwnLocationsStep;
import org.hive2hive.core.processes.implementations.logout.StopDownloadsStep;
import org.hive2hive.core.processes.implementations.logout.WritePersistentStep;
import org.hive2hive.core.processes.implementations.notify.BaseNotificationMessageFactory;
import org.hive2hive.core.processes.implementations.notify.GetAllLocationsStep;
import org.hive2hive.core.processes.implementations.notify.GetPublicKeysStep;
import org.hive2hive.core.processes.implementations.notify.PutAllUserProfileTasksStep;
import org.hive2hive.core.processes.implementations.notify.RemoveUnreachableStep;
import org.hive2hive.core.processes.implementations.notify.SendNotificationsMessageStep;
import org.hive2hive.core.processes.implementations.notify.VerifyNotificationFactoryStep;
import org.hive2hive.core.processes.implementations.register.CheckIsUserRegisteredStep;
import org.hive2hive.core.processes.implementations.register.LocationsCreationStep;
import org.hive2hive.core.processes.implementations.register.PutPublicKeyStep;
import org.hive2hive.core.processes.implementations.register.PutUserProfileStep;
import org.hive2hive.core.processes.implementations.register.UserProfileCreationStep;
import org.hive2hive.core.processes.implementations.share.PrepareNotificationsStep;
import org.hive2hive.core.processes.implementations.share.UpdateUserProfileStep;
import org.hive2hive.core.processes.implementations.share.VerifyFriendIdStep;
import org.hive2hive.core.processes.implementations.share.pkupdate.InitializeMetaUpdateStep;
import org.hive2hive.core.processes.implementations.userprofiletask.HandleUserProfileTaskStep;
import org.hive2hive.core.security.UserCredentials;

/**
 * Factory class for the creation of specific process components and composites that represent basic
 * operations of the Hive2Hive project.
 * 
 * @author Christian, Nico
 * 
 */
public final class ProcessFactory {

	private static ProcessFactory instance;

	public static ProcessFactory instance() {
		if (instance == null)
			instance = new ProcessFactory();
		return instance;
	}

	private ProcessFactory() {
		// singleton
	}

	/**
	 * Creates and returns a registration process.
	 * 
	 * @param credentials The credentials of the user to be registered.
	 * @param networkManager The network manager / node on which the registration operations should be
	 *            executed.
	 * @return A registration process.
	 * @throws NoPeerConnectionException
	 */
	public ProcessComponent createRegisterProcess(UserCredentials credentials, NetworkManager networkManager)
			throws NoPeerConnectionException {
		DataManager dataManager = networkManager.getDataManager();
		RegisterProcessContext context = new RegisterProcessContext();

		// process composition
		SequentialProcess process = new SequentialProcess();

		process.add(new CheckIsUserRegisteredStep(credentials.getUserId(), context, dataManager));
		process.add(new LocationsCreationStep(credentials.getUserId(), context));
		process.add(new UserProfileCreationStep(credentials.getUserId(), context));
		process.add(new AsyncComponent(new PutUserProfileStep(credentials, context, dataManager)));
		process.add(new AsyncComponent(new PutUserLocationsStep(context, context, dataManager)));
		process.add(new AsyncComponent(new PutPublicKeyStep(context, dataManager)));

		return process;
	}

	/**
	 * Creates and returns a login process.
	 * 
	 * @param credentials The credentials of the user to be logged in.
	 * @param params The session parameters that shall be used.
	 * @param networkManager The network manager / node on which the login operations should be executed.
	 * @return A login process.
	 * @throws NoPeerConnectionException
	 */
	public ProcessComponent createLoginProcess(UserCredentials credentials, SessionParameters params,
			NetworkManager networkManager) throws NoPeerConnectionException {
		DataManager dataManager = networkManager.getDataManager();
		LoginProcessContext context = new LoginProcessContext();

		// process composition
		SequentialProcess process = new SequentialProcess();

		process.add(new GetUserProfileStep(credentials, context, dataManager));
		process.add(new SessionCreationStep(params, context, networkManager));
		process.add(new GetUserLocationsStep(credentials.getUserId(), context, networkManager
				.getDataManager()));
		process.add(new ContactOtherClientsStep(context, networkManager));
		process.add(new PutUserLocationsStep(context, context, dataManager));
		process.add(new SynchronizeFilesStep(context, networkManager));

		return process;
	}

	public ProcessComponent createUserProfileTaskStep(NetworkManager networkManager) {
		SequentialProcess process = new SequentialProcess();
		UserProfileTaskContext context = new UserProfileTaskContext();
		process.add(new GetUserProfileTaskStep(context, networkManager));
		// Note: this step will add the next steps since it depends on the get result
		process.add(new HandleUserProfileTaskStep(context, networkManager));

		return process;
	}

	/**
	 * Creates and returns a logout process.
	 * 
	 * @param networkManager The network manager / node on which the logout operations should be executed.
	 * @return A logout process.
	 * @throws NoPeerConnectionException
	 * @throws NoSessionException
	 */
	public ProcessComponent createLogoutProcess(NetworkManager networkManager)
			throws NoPeerConnectionException, NoSessionException {
		DataManager dataManager = networkManager.getDataManager();
		H2HSession session = networkManager.getSession();
		LogoutProcessContext context = new LogoutProcessContext(session);

		// process composition
		SequentialProcess process = new SequentialProcess();

		process.add(new GetUserLocationsStep(session.getCredentials().getUserId(), context, dataManager));
		process.add(new RemoveOwnLocationsStep(context, networkManager));
		process.add(new StopDownloadsStep(session.getDownloadManager()));
		process.add(new WritePersistentStep(session.getRoot(), session.getKeyManager(), session
				.getDownloadManager()));
		process.add(new DeleteSessionStep(networkManager));

		// TODO to be implemented:
		// // stop all running processes
		// ProcessManager.getInstance().stopAll("Logout stopped all processes.");

		return process;
	}

	/**
	 * Process to create a new file. Note that this is only applicable for a single file, not a whole file
	 * tree.
	 */
	public ProcessComponent createNewFileProcess(File file, NetworkManager networkManager)
			throws NoSessionException, NoPeerConnectionException {
		H2HSession session = networkManager.getSession();
		DataManager dataManager = networkManager.getDataManager();
		AddFileProcessContext context = new AddFileProcessContext(file);

		SequentialProcess process = new SequentialProcess();
		process.add(new ValidateFileSizeStep(context, session.getFileConfiguration(), true));
		process.add(new CheckWriteAccessStep(context, session.getProfileManager(), session.getRoot()));
		if (file.isFile()) {
			// file needs to upload the chunks and a meta file
			process.add(new InitializeChunksStep(context, dataManager, session.getFileConfiguration()));
			process.add(new CreateMetaFileStep(context));
			process.add(new PutMetaFileStep(context, dataManager));
		}
		process.add(new AddIndexToUserProfileStep(context, session.getProfileManager(), session.getRoot()));
		process.add(new PrepareNotificationStep(context));
		process.add(createNotificationProcess(context, networkManager));

		return process;
	}

	public ProcessComponent createUpdateFileProcess(File file, NetworkManager networkManager)
			throws NoSessionException, IllegalArgumentException, NoPeerConnectionException {
		DataManager dataManager = networkManager.getDataManager();
		UpdateFileProcessContext context = new UpdateFileProcessContext(file);

		H2HSession session = networkManager.getSession();

		SequentialProcess process = new SequentialProcess();
		process.add(new ValidateFileSizeStep(context, session.getFileConfiguration(), false));
		process.add(new CheckWriteAccessStep(context, session.getProfileManager(), session.getRoot()));
		process.add(new File2MetaFileComponent(file, context, context, networkManager));
		process.add(new InitializeChunksStep(context, dataManager, session.getFileConfiguration()));
		process.add(new CreateNewVersionStep(context, session.getFileConfiguration()));
		process.add(new PutMetaFileStep(context, dataManager));
		process.add(new UpdateMD5inUserProfileStep(context, session.getProfileManager()));

		// TODO: cleanup can be made async because user operation does not depend on it
		process.add(new CleanupChunksStep(context, dataManager));
		process.add(new PrepareNotificationStep(context));
		process.add(createNotificationProcess(context, networkManager));

		return process;
	}

	/**
	 * Process for downloading the newest version to the default location.
	 */
	public ProcessComponent createDownloadFileProcess(PublicKey fileKey, NetworkManager networkManager)
			throws NoSessionException {
		return createDownloadFileProcess(fileKey, DownloadFileContext.NEWEST_VERSION_INDEX, null,
				networkManager);
	}

	/**
	 * Process for downloading with some extra parameters. This can for example be used to restore a file. The
	 * version and the filename are only effective for files, not for folders.
	 */
	public ProcessComponent createDownloadFileProcess(PublicKey fileKey, int versionToDownload,
			File destination, NetworkManager networkManager) throws NoSessionException {
		// precondition: session is existent
		networkManager.getSession();

		SequentialProcess process = new SequentialProcess();
		DownloadFileContext context = new DownloadFileContext(fileKey, destination, versionToDownload);
		process.add(new FindInUserProfileStep(context, networkManager));

		return process;
	}

	/**
	 * Deletes the specified file. Note that this is only valid for a single file or an empty folder
	 * (non-recursive)
	 */
	public ProcessComponent createDeleteFileProcess(File file, NetworkManager networkManager)
			throws NoSessionException, NoPeerConnectionException {
		DeleteFileProcessContext context = new DeleteFileProcessContext(file.isDirectory());

		// process composition
		SequentialProcess process = new SequentialProcess();

		// hint: this step automatically adds additional process steps when the meta file and the chunks need
		// to be deleted
		process.add(new DeleteFromUserProfileStep(file, context, networkManager));
		process.add(new DeleteFileOnDiskStep(file)); // TODO make asynchronous
		process.add(new PrepareDeleteNotificationStep(context));
		process.add(createNotificationProcess(context, networkManager));

		return process;
	}

	public ProcessComponent createMoveFileProcess(File source, File destination, NetworkManager networkManager)
			throws NoSessionException, NoPeerConnectionException {
		MoveFileProcessContext context = new MoveFileProcessContext(source, destination,
				networkManager.getUserId());

		SequentialProcess process = new SequentialProcess();
		process.add(new MoveOnDiskStep(context, networkManager));
		process.add(new RelinkUserProfileStep(context, networkManager));
		process.add(createNotificationProcess(context.getMoveNotificationContext(), networkManager));
		process.add(createNotificationProcess(context.getDeleteNotificationContext(), networkManager));
		process.add(createNotificationProcess(context.getAddNotificationContext(), networkManager));

		return process;
	}

	public ProcessComponent createRecoverFileProcess(File file, IVersionSelector selector,
			NetworkManager networkManager) throws NoSessionException, NoPeerConnectionException {
		RecoverFileContext context = new RecoverFileContext(file);
		SequentialProcess process = new SequentialProcess();
		process.add(new File2MetaFileComponent(file, context, context, networkManager));
		process.add(new SelectVersionStep(context, selector, networkManager));

		return process;
	}

	public ProcessComponent createShareProcess(File folder, UserPermission permission,
			NetworkManager networkManager) throws NoSessionException, NoPeerConnectionException {
		ShareProcessContext context = new ShareProcessContext(folder, permission);
	
		SequentialProcess process = new SequentialProcess();
		process.add(new VerifyFriendIdStep(networkManager.getSession().getKeyManager(), permission
				.getUserId()));
		process.add(new UpdateUserProfileStep(context, networkManager.getSession()));
		process.add(new InitializeMetaUpdateStep(context, networkManager.getDataManager()));
		process.add(new PrepareNotificationsStep(context, networkManager.getUserId()));
		process.add(createNotificationProcess(context, networkManager));
	
		return process;
	}

	/**
	 * Creates and returns a file list process.
	 * 
	 * @param networkManager The network manager / node on which the file list operations should be executed.
	 * @return A file list process.
	 * @throws NoSessionException
	 */
	public IResultProcessComponent<List<FileTaste>> createFileListProcess(NetworkManager networkManager)
			throws NoSessionException {
		H2HSession session = networkManager.getSession();
		GetFileListStep listStep = new GetFileListStep(session.getProfileManager(), session.getRootFile());
		return new AsyncResultComponent<List<FileTaste>>(listStep);
	}

	public ProcessComponent createNotificationProcess(final BaseNotificationMessageFactory messageFactory,
			final Set<String> usersToNotify, NetworkManager networkManager) throws IllegalArgumentException,
			NoPeerConnectionException, NoSessionException {
		// create a context here to provide the necessary data
		IConsumeNotificationFactory context = new IConsumeNotificationFactory() {

			@Override
			public Set<String> consumeUsersToNotify() {
				return usersToNotify;
			}

			@Override
			public BaseNotificationMessageFactory consumeMessageFactory() {
				return messageFactory;
			}
		};
		return createNotificationProcess(context, networkManager);
	}

	private ProcessComponent createNotificationProcess(IConsumeNotificationFactory providerContext,
			NetworkManager networkManager) throws IllegalArgumentException, NoPeerConnectionException,
			NoSessionException {
		NotifyProcessContext context = new NotifyProcessContext(providerContext);

		SequentialProcess process = new SequentialProcess();
		process.add(new VerifyNotificationFactoryStep(context, networkManager.getUserId()));
		process.add(new GetPublicKeysStep(context, networkManager.getSession().getKeyManager()));
		process.add(new PutAllUserProfileTasksStep(context, networkManager));
		process.add(new GetAllLocationsStep(context, networkManager.getDataManager()));
		process.add(new SendNotificationsMessageStep(context, networkManager));
		// cleanup my own locations
		process.add(new GetUserLocationsStep(networkManager.getUserId(), context, networkManager
				.getDataManager()));
		process.add(new RemoveUnreachableStep(context, networkManager));

		return process;
	}
}
