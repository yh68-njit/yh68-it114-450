package Project.Common;

public enum PayloadType {
    CLIENT_CONNECT, // client requesting to connect to server (passing of initialization data [name])
    CLIENT_ID,  // server sending client id
    SYNC_CLIENT,  // silent syncing of clients in room
    DISCONNECT,  // distinct disconnect action
    ROOM_CREATE,
    ROOM_JOIN, // join/leave room based on boolean
<<<<<<< HEAD:Project/Common/PayloadType.java
    MESSAGE, // sender and message,
    ROOM_LIST, // client: query for rooms, server: result of query,
    ROLL,
    MUTE,
    UNMUTE,
    PRIVATE_MESSAGE
=======
    MESSAGE, // sender and message
    ROLL
>>>>>>> 038c00d28b3f646155a23cc23b069f69ddda3e59:Project/PayloadType.java
}
