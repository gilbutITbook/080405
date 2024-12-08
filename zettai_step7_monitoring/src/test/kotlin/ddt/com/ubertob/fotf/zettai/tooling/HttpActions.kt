package ddt.com.ubertob.fotf.zettai.tooling

import com.ubertob.fotf.zettai.db.resetDatabase
import com.ubertob.fotf.zettai.domain.*
import com.ubertob.fotf.zettai.domain.tooling.expectSuccess
import com.ubertob.fotf.zettai.fp.asSuccess
import com.ubertob.fotf.zettai.ui.HtmlPage
import com.ubertob.fotf.zettai.ui.toIsoLocalDate
import com.ubertob.fotf.zettai.ui.toStatus
import com.ubertob.pesticide.core.*
import org.http4k.client.JettyClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.Form
import org.http4k.core.body.toBody
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.time.Duration

data class HttpActions(val env: String = "local") : ZettaiActions {

    val zettaiPort = 8000 //different from the one in main

    val client = JettyClient()

    override val protocol: DdtProtocol = Http(env)
    override fun prepare(): DomainSetUp {

        if (verifyStarted(Duration.ZERO) == Ready)
            return Ready

        resetDatabase(pgDataSourceForTest())

        val server = prepareZettaiOnTestDatabase().asServer(Jetty(zettaiPort))
        server.start()
        registerShutdownHook {
            server.stop()
        }

        return verifyStarted(Duration.ofSeconds(2))
    }


    private fun registerShutdownHook(hookToExecute: () -> Unit) {
        Runtime.getRuntime().addShutdownHook(Thread {
            val out = System.out
            try {
                hookToExecute()
            } finally {
                System.setOut(out)
            }
        })
    }

    override fun getToDoList(user: User, listName: ListName): ZettaiOutcome<ToDoList> {

        val response = callZettai(Method.GET, todoListUrl(user, listName))

        expectThat(response.status).isEqualTo(Status.OK)

        val html = HtmlPage(response.bodyString())

        val items = extractItemsFromPage(html)

        return ToDoList(listName, items).asSuccess()
    }


    override fun addListItem(user: User, listName: ListName, item: ToDoItem) {
        val response = submitToZettai(
            addItemUrl(user, listName),
            listItemForm(item)
        )

        expect {
            that(response.bodyString()).isEmpty()
            that(response.status).isEqualTo(Status.SEE_OTHER)
        }

    }

    override fun allUserLists(user: User): ZettaiOutcome<List<ListName>> {
        val response = callZettai(Method.GET, allUserListsUrl(user))

        expectThat(response.status).isEqualTo(Status.OK)

        val html = HtmlPage(response.bodyString())

        val names = extractListNamesFromPage(html)

        return names.map { name -> ListName.fromTrusted(name) }.asSuccess()
    }

    override fun whatsNext(user: User): ZettaiOutcome<List<ToDoItem>> {
        val response = callZettai(Method.GET, whatsNextUrl(user))

        expectThat(response.status).isEqualTo(Status.OK)

        val html = HtmlPage(response.bodyString())

        val items = extractItemsFromPage(html)

        return items.asSuccess()
    }

    override fun ToDoListOwner.`starts with a list`(listName: String, items: List<String>) {
        val list = ListName.fromTrusted(listName)
        val lists = allUserLists(user).expectSuccess()
        if (list !in lists) {
            val response = submitToZettai(allUserListsUrl(user), newListForm(list))

            expectThat(response.status).isEqualTo(Status.SEE_OTHER)  //redirect same page

            items.forEach {
                addListItem(user, list, ToDoItem(it))
            }

        }
    }

    override fun renameList(user: User, oldName: ListName, newName: ListName) {
        val response = submitToZettai(renameListUrl(user, oldName), renameListForm(newName))

        expect {
            that(response.bodyString()).isEmpty()
            that(response.status).isEqualTo(Status.SEE_OTHER)
        }
    }

    override fun deleteListItem(user: User, listName: ListName, itemName: String) {
        val response = submitToZettai(deleteItemUrl(user, listName), oldItemNameForm(itemName))

        expect {
            that(response.bodyString()).isEmpty()
            that(response.status).isEqualTo(Status.SEE_OTHER)
        }
    }

    override fun updateListItem(user: User, listName: ListName, oldItemName: String, newItem: ToDoItem) {
        val response =
            submitToZettai(updateItemUrl(user, listName), listItemForm(newItem) + oldItemNameForm(oldItemName))

        expect {
            that(response.bodyString()).isEmpty()
            that(response.status).isEqualTo(Status.SEE_OTHER)
        }
    }


    private fun listItemForm(item: ToDoItem): Form =
        listOf("itemname" to item.description, "itemdue" to item.dueDate?.toString(), "status" to item.status.name)

    private fun newListForm(listName: ListName): Form = listOf("listname" to listName.name)
    private fun renameListForm(newName: ListName): Form = listOf("newListName" to newName.name)
    private fun oldItemNameForm(itemName: String): Form = listOf("olditemname" to itemName)

    private fun todoListUrl(user: User, listName: ListName) =
        "todo/${user.name}/${listName.name}"

    private fun renameListUrl(user: User, listName: ListName) =
        "todo/${user.name}/${listName.name}/rename"

    private fun addItemUrl(user: User, listName: ListName) =
        "todo/${user.name}/${listName.name}/additem"

    private fun deleteItemUrl(user: User, listName: ListName) =
        "todo/${user.name}/${listName.name}/deleteitem"

    private fun updateItemUrl(user: User, listName: ListName) =
        "todo/${user.name}/${listName.name}/updateitem"

    private fun allUserListsUrl(user: User) =
        "todo/${user.name}"

    private fun whatsNextUrl(user: User) =
        "whatsnext/${user.name}"

    private fun extractItemsFromPage(html: HtmlPage): List<ToDoItem> {
        return html.parse()
            .select("""[data-test="item"]""")
            .map {
                Triple(
                    it.select("[data-test=\"item-desc\"]").text().orEmpty(),
                    it.select("[data-test=\"item-due\"]").text().toIsoLocalDate(),
                    it.select("[data-test=\"item-status\"]").text().toStatus()
                )
            }
            .map { (name, date, status) ->
                ToDoItem(name, date, status)
            }
    }

    private fun extractListNamesFromPage(html: HtmlPage): List<String> {
        return html.parse()
            .select("[data-test=\"list-name\"]")
            .mapNotNull {
                it.text()
            }
    }


    private fun verifyStarted(timeout: Duration): DomainSetUp {
        val begin = System.currentTimeMillis()
        while (true) {
            val r = callZettai(Method.GET, "ping").status
            if (r == Status.OK)
                return Ready
            if (elapsed(begin) >= timeout)
                return NotReady("timeout $timeout exceeded")
            Thread.sleep(10)
        }
    }

    private fun elapsed(since: Long): Duration =
        Duration.ofMillis(System.currentTimeMillis() - since)


    private fun submitToZettai(path: String, webForm: Form): Response =
        client(log(Request(Method.POST, "http://localhost:$zettaiPort/$path").body(webForm.toBody())))

    private fun callZettai(method: Method, path: String): Response =
        client(log(Request(method, "http://localhost:$zettaiPort/$path")))

    fun <T> log(something: T): T {
        println("--- $something")
        return something
    }

    private fun HtmlPage.parse(): Document = Jsoup.parse(raw.toString())

}

