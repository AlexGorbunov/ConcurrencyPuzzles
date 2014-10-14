/**
 * Created by Oleksandr_Gorbunov on 10/14/2014.
 */
case class TweetMessage(message: String, time: Long, actorName: String) extends Ordered[TweetMessage] {
  val tweetMessage = message
  val messageTime = time
  val creator = actorName

  override def compare(that: TweetMessage) = (that.messageTime - this.messageTime).toInt
  override def toString(): String = tweetMessage + getAccurateTime()

  def asFullTweet(): String = creator + " - " + toString()

  def getAccurateTime(): String = {
    val value = (System.currentTimeMillis() - messageTime) / 1000

    var result = " ("
    if ((value / 3600) > 0)
      result += (value / 3600) + " hours "
    else if ((value / 60) > 0)
      result += (value / 60) + " minutes "
    else
      result += (value) + " seconds "

    return result + " ago)"
  }
}
