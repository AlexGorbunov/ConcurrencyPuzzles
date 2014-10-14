import akka.actor.{ActorRef, Actor}

import scala.collection.immutable.TreeSet
import scala.collection.mutable
import scala.concurrent.{Await, Future}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

/**
 * Created by Oleksandr_Gorbunov on 10/14/2014.
 */
class WorkingActor extends Actor {
  val usersToFollow: mutable.HashSet[ActorRef] = mutable.HashSet()
  val tweetsList : mutable.MutableList[TweetMessage] = mutable.MutableList()

  def printTweets(args: mutable.PriorityQueue[_]): Unit = {
    args.foreach(println)
  }

  def getTweets(args: mutable.MutableList[_]): String = {
    val builder = new mutable.StringBuilder
    builder ++=("\n")
    val iter = args.reverseIterator
    while (iter.hasNext) {
      builder ++=(iter.next().toString)
      builder ++=("\n")
    }
    return builder.toString()
  }

  override def receive: Receive = initial()

  def initial(): Receive = {
    case "Post" => {
      context.become(posting)
    }
    case "Read" => {
      context.become(reading)
      reading()
    }
    case "Follow" => {
      context.become(following)
    }
    case "Wall" => {
      context.become(showWall)
      showWall()
    }
    case "Finish" => context.stop(self)
    case mes => unhandled(mes)
  }

  def posting: Receive = {
    case message => {
      tweetsList += TweetMessage(message.toString, System.currentTimeMillis(), self.path.name)
      context.unbecome()
    }
  }

  def reading: Receive = {
    case message => {
      sender() ! tweetsList.clone()
      context.unbecome()
    }
  }

  def following(): Receive = {
    case message => {
      if (message.isInstanceOf[ActorRef]) {
        usersToFollow += (message.asInstanceOf[ActorRef])
        sender() ! "OK"
      } else {
        sender() ! "FAULT"
      }
      context.unbecome()
    }
  }

  def showWall: Receive = {
    case message => {
      var sortedTweetsSet : TreeSet[TweetMessage] = new TreeSet[TweetMessage]()
      sortedTweetsSet ++= (tweetsList.clone())
      usersToFollow.foreach(s => {
        implicit val timeout = Timeout(2 seconds)
        val future: Future[mutable.MutableList[TweetMessage]] =
          ask(s, "Read").mapTo[mutable.MutableList[TweetMessage]]
        val result : mutable.MutableList[TweetMessage] = Await.result(future, timeout.duration)
        sortedTweetsSet ++= (result)
      })
      sender ! sortedTweetsSet
      context.unbecome()
    }
  }

  override def hashCode = self.path.name.hashCode
}
