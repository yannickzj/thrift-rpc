exception IllegalArgument {
    1: string message;
}

service BcryptService {
    // Client -> FE RPC functions
    list<string> hashPassword (1: list<string> password, 2: i16 logRounds) throws (1: IllegalArgument e);
    list<bool> checkPassword (1: list<string> password, 2: list<string> hash) throws (1: IllegalArgument e);

    // FE -> BE RPC functions
    list<string> executeHashPassword (1: list<string> password, 2: i16 logRounds);
    list<bool> executeCheckPassword (1: list<string> password, 2: list<string> hash);

    // BE -> FE RPC functions
    bool registerBE (1: string host, 2: i32 port);

    // FE <-> BE RPC functions
    string isAlive(1: string msg);
}

