package com.twitter.flockdb.shards

import scala.collection.mutable
import com.twitter.results.{Cursor, ResultWindow}
import com.twitter.service.flock.State
import com.twitter.gizzard.shards
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._


class BlackHoleShardFactory extends shards.ShardFactory[Shard] {
  def instantiate(shardInfo: shards.ShardInfo, weight: Int, children: Seq[Shard]) =
    new BlackHoleShard(shardInfo, weight, children)
  def materialize(shardInfo: shards.ShardInfo) = ()
}

class BlackHoleShard(val shardInfo: shards.ShardInfo, val weight: Int, val children: Seq[Shard]) extends Shard {
  def remove(sourceId: Long, updatedAt: Time) = ()

  def getMetadata(sourceId: Long) = None

  def withLock[A](sourceId: Long)(f: (Shard, Metadata) => A) = f(this, Metadata(sourceId, State.Normal, 1, Time.now))

  def remove(sourceId: Long, destinationId: Long, position: Long, updatedAt: Time) = State.Removed

  def selectIncludingArchived(sourceId: Long, count: Int, cursor: Cursor) = new ResultWindow[Long]

  def intersect(sourceId: Long, states: Seq[State], destinationIds: Seq[Long]) = Nil

  def intersectEdges(sourceId: Long, states: Seq[State], destinationIds: Seq[Long]) = Nil

  def selectByDestinationId(sourceId: Long, states: Seq[State], count: Int, cursor: Cursor) = new ResultWindow[Long]

  def selectAll(cursor: (Cursor, Cursor), count: Int) = (Nil, (Cursor.End, Cursor.End))

  def selectAllMetadata(cursor: Cursor, count: Int) = (Nil, Cursor.End)

  def writeCopies(edges: Seq[Edge]) = ()

  def writeMetadata(metadata: Metadata) = ()

  def updateMetadata(metadata: Metadata) = ()

  def add(sourceId: Long, updatedAt: Time) = ()

  def add(sourceId: Long, destinationId: Long, position: Long, updatedAt: Time) = State.Normal

  def negate(sourceId: Long, updatedAt: Time) = ()

  def negate(sourceId: Long, destinationId: Long, position: Long, updatedAt: Time) = State.Normal

  def get(sourceId: Long, destinationId: Long) = None

  def count(sourceId: Long, states: Seq[State]) = 0

  def counts(sourceIds: Seq[Long], results: mutable.Map[Long, Int]) = ()

  def archive(sourceId: Long, destinationId: Long, position: Long, updatedAt: Time) = State.Archived

  def archive(sourceId: Long, updatedAt: Time) = ()

  def selectByPosition(sourceId: Long, states: Seq[State], count: Int, cursor: Cursor) = new ResultWindow[Long]

  def selectEdges(sourceId: Long, states: Seq[State], count: Int, cursor: Cursor) = new ResultWindow[Edge]
}
