package Project.Common;

public enum PayloadType {
    CLIENT_CONNECT, // client requesting to connect to server (passing of initialization data [name])
    CLIENT_ID,  // server sending client id
    SYNC_CLIENT,  // silent syncing of clients in room
    DISCONNECT,  // distinct disconnect action
    ROOM_CREATE,
    ROOM_JOIN, // join/leave room based on boolean
    MESSAGE, // sender and message,
    ROOM_LIST, // client: query for rooms, server: result of query
    ROLL,
    FLIP,
    MUTE,
    UNMUTE,
    PRIVATE_MESSAGE,
    MUTE_UNMUTE_NOTIFICATION,
    USER_STATUS_UPDATE
}
