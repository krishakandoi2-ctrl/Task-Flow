package taskflow.common;

public class Protocol {
    public static final String LOGIN               = "LOGIN";
    public static final String LOGOUT              = "LOGOUT";
    public static final String CREATE_TASK         = "CREATE_TASK";
    public static final String GET_TASKS           = "GET_TASKS";
    public static final String UPDATE_STATUS       = "UPDATE_STATUS";
    public static final String UPDATE_NOTES        = "UPDATE_NOTES";
    public static final String DELETE_TASK         = "DELETE_TASK";
    public static final String CREATE_PERSONAL_TASK= "CREATE_PERSONAL_TASK";
    public static final String GET_FOLDERS         = "GET_FOLDERS";
    public static final String GET_PERSONAL_TASKS    = "GET_PERSONAL_TASKS";
    public static final String DELETE_PERSONAL_TASK  = "DELETE_PERSONAL_TASK";
    public static final String DELETE_USER           = "DELETE_USER";
    public static final String GET_USERS           = "GET_USERS";
    public static final String ADD_USER            = "ADD_USER";
    public static final String OK                  = "OK";
    public static final String ERROR               = "ERROR";
    public static final String DATA                = "DATA";
    public static final String ITEM_SEP            = "~~";
    private Protocol() {}
}