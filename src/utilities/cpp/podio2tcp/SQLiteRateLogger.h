#ifndef RTDP_SQLITERATELOGGER_H
#define RTDP_SQLITERATELOGGER_H

#include <sqlite3.h>
#include <string>
#include <iostream>
#include <sstream>
#include <stdexcept>

/**
 * This class manages a SQLite3 DB for writing and querying the "rate_logs" table.
 */

class SQLiteRateLogger {
public:
    const std::string TABLENAME = "rate_logs";

private:
    sqlite3 *db;
    std::string db_name;

public:
    SQLiteRateLogger() : db(nullptr), db_name("") {}

    ~SQLiteRateLogger() {
        closeDB();
    }

    bool openDB(const std::string& input_dbname) {
        db_name = input_dbname;
        int rc = sqlite3_open(db_name.c_str(), &db);
        if (rc) {
            std::cerr << "Can't open database: " << sqlite3_errmsg(db) << std::endl;
            return false;
        }
        std::cout << "Opened database [" << db_name << "] successfully!" << std::endl;

        // outputTableSchema();

        return true;
    }

    void outputTableSchema() {
        if (!db) {
            throw std::runtime_error("Database not open.");
        }

        std::string query = "PRAGMA table_info(" + TABLENAME + ");";
        char* errMsg = nullptr;

        auto callback = [](void*, int argc, char** argv, char** colName) -> int {
            std::cout << "Column Information:\n";
            for (int i = 0; i < argc; ++i) {
                std::cout << colName[i] << ": " << (argv[i] ? argv[i] : "NULL") << "\n";
            }
            std::cout << "------------------------------------\n";
            return 0;
        };

        int rc = sqlite3_exec(db, query.c_str(), callback, nullptr, &errMsg);
        if (rc != SQLITE_OK) {
            std::string error = "SQL error: " + std::string(errMsg);
            sqlite3_free(errMsg);
            throw std::runtime_error(error);
        }

    // Column Details in PRAGMA table_info
    // Each row corresponds to a column in the table, and the fields are as follows:
    // Index	Name	Description
    // cid	Column ID	The column's index in the table (starting from 0).
    // name	Column Name	The name of the column.
    // type	Column Type	The data type of the column (e.g., INTEGER, REAL).
    // notnull	NOT NULL	1 if the column is declared as NOT NULL; otherwise, 0 if it can accept NULL values.
    // dflt_value	Default Value	The default value for the column if specified; otherwise NULL.
    // pk	Primary Key	1 if the column is part of the primary key; otherwise, 0.

    }

    bool insertRateLog(const std::string &columns, const std::string &values) {
        if (!db)
        {
            std::cerr << "Database not open." << std::endl;
            return false;
        }

        std::string sql = "INSERT INTO " + TABLENAME + " (" + columns + ") VALUES (" + values + ");";
        char *errMsg = nullptr;

        int rc = sqlite3_exec(db, sql.c_str(), nullptr, nullptr, &errMsg);
        if (rc != SQLITE_OK)
        {
            std::cerr << "SQL error: " << errMsg << std::endl;
            sqlite3_free(errMsg);
            return false;
        }
        // std::cout << "Record inserted successfully." << std::endl;
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
