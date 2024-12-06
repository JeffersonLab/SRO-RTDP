#ifndef RTDP_SQLITERATELOGGER_H
#define RTDP_SQLITERATELOGGER_H

#include <sqlite3.h>
#include <string>
#include <iostream>
#include <sstream>
#include <stdexcept>

// // Create table to store data
// std::string createTableSQL = "CREATE TABLE IF NOT EXISTS DataLog ("
//                              "id INTEGER PRIMARY KEY AUTOINCREMENT,"
//                              "hostname TEXT,"
//                              "timestamp_utc INTEGER,"
//                              "pid INTEGER,"
//                              "rateHz_read_period REAL,"
//                              "rateHz_sent_period REAL,"
//                              "rateMbps_read_period REAL,"
//                              "rateMbps_sent_period REAL,"
//                              "rateHz_read_total REAL,"
//                              "rateHz_sent_total REAL,"
//                              "rateMbps_read_total REAL,"
//                              "rateMbps_sent_total REAL"
//                              ");";

/**
 * This open a SQLite3 DB according to the input DB name and write records into it.
 */
class SQLiteRateLogger {

private:
    sqlite3 *db;
    std::string db_name;

public:
    SQLiteRateLogger(const std::string &dbname) : db(nullptr), db_name(dbname) {}

    ~SQLiteRateLogger() {
        closeDB();
    }

    bool openDB() {
        int rc = sqlite3_open(db_name.c_str(), &db);
        if (rc) {
            std::cerr << "Can't open database: " << sqlite3_errmsg(db) << std::endl;
            return false;
        }
        std::cout << "Opened database successfully." << std::endl;
        return true;
    }

    void outputTableFormat() {
        if (!db) {
            std::cerr << "Database not open." << std::endl;
            return;
        }

        std::string sql = "SELECT name FROM sqlite_master WHERE type='table';";
        char *errMsg = nullptr;

        auto callback = [](void *NotUsed, int argc, char **argv, char **azColName) -> int {
            for (int i = 0; i < argc; i++)
            {
                std::cout << azColName[i] << ": " << (argv[i] ? argv[i] : "NULL") << std::endl;
            }
            return 0;
        };

        int rc = sqlite3_exec(db, sql.c_str(), callback, nullptr, &errMsg);
        if (rc != SQLITE_OK) {
            std::cerr << "SQL error: " << errMsg << std::endl;
            sqlite3_free(errMsg);
        }
        else {
            std::cout << "Table format outputted successfully." << std::endl;
        }
    }

    bool insertData(const std::string &table, const std::string &columns, const std::string &values)
    {
        if (!db)
        {
            std::cerr << "Database not open." << std::endl;
            return false;
        }

        std::string sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ");";
        char *errMsg = nullptr;

        int rc = sqlite3_exec(db, sql.c_str(), nullptr, nullptr, &errMsg);
        if (rc != SQLITE_OK)
        {
            std::cerr << "SQL error: " << errMsg << std::endl;
            sqlite3_free(errMsg);
            return false;
        }
        std::cout << "Record inserted successfully." << std::endl;
        return true;
    }

    void closeDB()
    {
        if (db) {
            sqlite3_close(db);
            db = nullptr;
            std::cout << "Database closed successfully." << std::endl;
        }
    }
};

#endif // RTDP_SQLITERATELOGGER_H
