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

  override def receive: Receive = initial()

  def initial(): Receive = {
    case "Post" => {
      context.become(posting)
    }
    case "Read" => {
      sender() ! tweetsList.clone()
    }
    case "Follow" => {
      context.become(following)
    }
    case "Wall" => {
      var sortedTweetsSet : TreeSet[TweetMessage] = new TreeSet[TweetMessage]()

      implicit val timeout = Timeout(2 seconds)

      var futuresList: List[Future[mutable.MutableList[TweetMessage]]] = List()
      usersToFollow.foreach(s => {
        futuresList = futuresList.::(
          ask(s, "Read").mapTo[mutable.MutableList[TweetMessage]] )
      })

      sortedTweetsSet ++= (tweetsList.clone())
      futuresList.foreach(e => {
        val result : mutable.MutableList[TweetMessage] = Await.result(e, timeout.duration)
        sortedTweetsSet ++= (result)
      })

      sender ! sortedTweetsSet
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

  override def hashCode = self.path.name.hashCode
}
