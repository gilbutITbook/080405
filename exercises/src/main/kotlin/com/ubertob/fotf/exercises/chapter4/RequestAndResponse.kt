package com.ubertob.fotf.exercises.chapter4

import java.net.URI

typealias User = String
typealias ListName = String
typealias ToDoList = List<String>
typealias Html = String

data class Request(
    val method: String,
    val uri: URI,
    val body: String
)

data class Response(
    val status: Int,
    val body: String
)

fun extractListData(request: Request): Pair<User, ListName>? {
    //expected req path "zettai/user/listname"
    val frag = request.uri.path.split('/')

    return if (frag.size != 4)
        null
    else {
        val user = frag[2]
        val list = frag[3]
        user to list
    }
}

fun fetchListContent(listId: Pair<User, ListName>): ToDoList? =
    if (listId.first.length < 3)
        null
    else
        listOf(
            "${listId.first} buy milk",
            "complete ${listId.second}",
            "something else"
        )

fun renderHtml(todoList: ToDoList): Html =
    """
    <html>
        <body>
            <h1>Zettai</h1>
            <table>
                <tbody>${renderItems(todoList)}</tbody>
            </table>
        </body>
    </html>
    """.trimIndent()


private fun renderItems(items: List<String>) =
    items.map {
        """<tr><td>${it}</td></tr>""".trimIndent()
    }.joinToString("\n")

fun createResponse(html: Html): Response =
    Response(200, html)
