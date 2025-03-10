package tech.goksi.pterobot.database.impl

import dev.minn.jda.ktx.util.SLF4J
import tech.goksi.pterobot.database.DataStorage
import tech.goksi.pterobot.entities.ApiKey
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.system.exitProcess

class SQLiteImpl : DataStorage {
    private val connection: Connection
    private val logger by SLF4J

    init {
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:database.db")
        val statement = connection.createStatement()
        statement.addBatch(
            "create table if not exists Members(id integer not null primary key autoincrement, discordID bigint not null unique, apiID integer, foreign key(apiID) references Keys(id) on delete set null)"
        )
        statement.addBatch(
            "create table if not exists Keys(id integer not null primary key autoincrement, \"key\" char(48) not null unique, \"admin\" boolean not null)"
        )
        statement.addBatch(
            "create table if not exists Accounts(id integer not null primary key autoincrement, username varchar(25), memberID integer, foreign key(memberID) references Members(id))"
        )
        statement.addBatch(
            "pragma foreign_keys = ON"
        )
        statement.use {
            try {
                it.executeBatch()
            } catch (exception: SQLException) {
                logger.error("Failed to initialize SQLite database... exiting", exception)
                exitProcess(1)
            }
        }
    }

    override fun getApiKey(id: Long): ApiKey? {
        val statement = connection.prepareStatement(
            "select Keys.key, Keys.admin from Keys inner join Members on Keys.id = Members.apiID where Members.discordID = ?"
        )
        statement.setLong(1, id)
        statement.use {
            try {
                val resultSet = it.executeQuery()
                if (resultSet.next()) return ApiKey(resultSet.getString("key"), resultSet.getBoolean("admin"))
            } catch (exception: SQLException) {
                logger.error("Failed to get api key for $id", exception)
            }
            return null
        }
    }

    @Throws(SQLException::class)
    override fun link(id: Long, apiKey: ApiKey) {
        val keyStatement = connection.prepareStatement(
            "insert into Keys(\"key\", \"admin\") values (?, ?)"
        )
        val statement = connection.prepareStatement(
            "insert into Members(discordID, apiID) values (?, last_insert_rowid()) on conflict(discordID) do update set apiID = last_insert_rowid()"
        )
        statement.setLong(1, id)
        keyStatement.setString(1, apiKey.key)
        keyStatement.setBoolean(2, apiKey.admin)
        keyStatement.use { it.executeUpdate() }
        statement.use { it.executeUpdate() } //should catch SQLException later in command
    }

    override fun unlink(id: Long) {
        val statement = connection.prepareStatement(
            "delete from Keys where id in (" +
                    "select apiID from Members where discordID = ? )"
        )
        statement.setLong(1, id)
        statement.use {
            try {
                it.executeUpdate()
            } catch (exception: SQLException) {
                logger.error("Failed to delete api key for $id", exception)
            }
        }
    }
}