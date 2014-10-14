import akka.actor.{ActorRef, Props, ActorSystem, Actor}

import scala.collection.mutable
import scala.collection.immutable.TreeSet
import scala.io.StdIn
import scala.concurrent.{Await, Future}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

/**
 * Created by Oleksandr_Gorbunov on 10/13/2014.
 */

object ActorsApp {
  val system = ActorSystem("HelloSystem")
  var actors: Map[String, ActorRef] = Map()

  def retrieveActor(rawName: String) : ActorRef = {
    val actorName = rawName
    var currentActor : ActorRef = null
    if (actors.contains(actorName)) {
      currentActor = actors.get(actorName).get
    } else {
      currentActor = system.actorOf(Props[WorkingActor], name = actorName)
      actors += (actorName -> currentActor)
    }

    return currentActor
  }

  def main(args: Array[String]): Unit = {
    val in = StdIn

    while (true) {
      print("> ")
      val item = in.readLine()

      if ("quit".equals(item)) {
        system.shutdown()
        System.exit(0)
      }

      val words = item.toString.split(" ")
      if (words.size == 1) {
        val currentActor = retrieveActor(words(0))

        implicit val timeout = Timeout(2 seconds)
        val future: Future[mutable.MutableList[TweetMessage]] =
          ask(currentActor, "Read").mapTo[mutable.MutableList[TweetMessage]]
        val result = Await.result(future, timeout.duration)
        val iter = result.reverseIterator
        while (iter.hasNext) {
          println(iter.next().toString)
        }

      } else if (words.size > 1) {
        words(1) match {
          case "->" => {
            val currentActor = retrieveActor(words(0))

            var input = item.toString
            input = input.substring(
              (2 + 2 + words(0).length), input.length)

            currentActor ! "Post"
            currentActor ! input
          }
          case "follows" => {
            if (words.length == 3) {
              val currentActor = retrieveActor(words(0))

              val targetActor = words(2)
              val targetActorRef: ActorRef = actors.get(targetActor).get

              if (targetActorRef != null) {
                currentActor ! "Follow"

                implicit val timeout = Timeout(2 seconds)
                val future: Future[String] = ask(currentActor, targetActorRef).mapTo[String]
                val result = Await.result(future, timeout.duration)
              }
            }
          }
          case "wall" => {
            if (words.length == 2) {
              val currentActor = retrieveActor(words(0))

              if (currentActor != null) {
                implicit val timeout = Timeout(2 seconds)
                val future: Future[TreeSet[TweetMessage]] =
                  ask(currentActor, "Wall").mapTo[TreeSet[TweetMessage]]
                val result: TreeSet[TweetMessage] = Await.result(future, timeout.duration)

                result.foreach(e => {
                  e.asFullTweet()
                })
                val iter = result.iterator
                while (iter.hasNext) {
                  println(iter.next().asFullTweet())
                }
              }
            }
          }
          case _ => println ("Unknown operation")
      }
      }
    }

    system.shutdown()
  }
}
