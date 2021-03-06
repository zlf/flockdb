package com.twitter.flockdb.unit

import scala.collection.mutable
import com.twitter.gizzard.jobs.SchedulableWithTasks
import com.twitter.gizzard.scheduler.PrioritizingJobScheduler
import com.twitter.gizzard.thrift.conversions.Sequences._
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import com.twitter.service.flock.State
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import jobs.single
import jobs.multi
import queries.ExecuteCompiler
import operations.{ExecuteOperations, ExecuteOperation, ExecuteOperationType}


object ExecuteCompilerSpec extends Specification with JMocker with ClassMocker {
  def termToProgram(operationType: ExecuteOperationType.Value, term: QueryTerm, time: Option[Time], position: Option[Long]): ExecuteOperations = {
    val operation = new ExecuteOperation(operationType, term, position)
    val operations = List(operation)
    new ExecuteOperations(operations, time.map { _.inSeconds }, Priority.Low)
  }

  def termToProgram(operationType: ExecuteOperationType.Value, term: QueryTerm): ExecuteOperations = termToProgram(operationType, term, Some(Time.now))
  def termToProgram(operationType: ExecuteOperationType.Value, term: QueryTerm, time: Option[Time]): ExecuteOperations = termToProgram(operationType, term, time, Some(Time.now.inMillis))

  "ExecuteCompiler" should {
    val FOLLOWS = 1

    val alice = 1L
    val bob = 2L
    val carl = 3L
    var scheduler: PrioritizingJobScheduler = null
    var executeCompiler: ExecuteCompiler = null

    doBefore {
      Time.freeze()
      scheduler = mock[PrioritizingJobScheduler]
      executeCompiler = new ExecuteCompiler(scheduler)
    }

    "without execute_at present" in {
      val program = termToProgram(ExecuteOperationType.Add, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob)), List(State.Normal)), None)
      expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(single.Add(alice, FOLLOWS, bob, Time.now.inMillis, Time.now)))) }
      executeCompiler(program)
    }

    "without position present" in {
      val program = termToProgram(ExecuteOperationType.Add, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob)), List(State.Normal)), None, None)
      expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(single.Add(alice, FOLLOWS, bob, Time.now.inMillis, Time.now)))) }
      executeCompiler(program)
    }

    "compile add operations" in {
      "single" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Add, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(single.Add(alice, FOLLOWS, bob, Time.now.inMillis, Time.now)))) }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Add, new QueryTerm(alice, FOLLOWS, false, Some(List[Long](bob)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(single.Add(bob, FOLLOWS, alice, Time.now.inMillis, Time.now)))) }
          executeCompiler(program)
        }
      }

      "aggregate" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Add, new QueryTerm(alice, FOLLOWS, true, None, List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(multi.Unarchive(alice, FOLLOWS, Direction.Forward, Time.now, Priority.Low)))) }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Add, new QueryTerm(alice, FOLLOWS, false, None, List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(multi.Unarchive(alice, FOLLOWS, Direction.Backward, Time.now, Priority.Low)))) }
          executeCompiler(program)
        }
      }

      "multi" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Add, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob, carl)), List(State.Normal)))
          expect {
            one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(
              single.Add(alice, FOLLOWS, bob, Time.now.inMillis, Time.now),
              single.Add(alice, FOLLOWS, carl, Time.now.inMillis, Time.now))))
          }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Add, new QueryTerm(alice, FOLLOWS, false, Some(List[Long](bob, carl)), List(State.Normal)))
          expect {
            one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(
              single.Add(bob, FOLLOWS, alice, Time.now.inMillis, Time.now),
              single.Add(carl, FOLLOWS, alice, Time.now.inMillis, Time.now))))
          }
          executeCompiler(program)
        }
      }
    }

    "compile remove operations" in {
      "single" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Remove, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(new single.Remove(alice, FOLLOWS, bob, Time.now.inMillis, Time.now)))) }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Remove, new QueryTerm(alice, FOLLOWS, false, Some(List[Long](bob)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(new single.Remove(bob, FOLLOWS, alice, Time.now.inMillis, Time.now)))) }
          executeCompiler(program)
        }
      }

      "aggregate" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Remove, new QueryTerm(alice, FOLLOWS, true, None, List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(multi.RemoveAll(alice, FOLLOWS, Direction.Forward, Time.now, Priority.Low)))) }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Remove, new QueryTerm(alice, FOLLOWS, false, None, List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(multi.RemoveAll(alice, FOLLOWS, Direction.Backward, Time.now, Priority.Low)))) }
          executeCompiler(program)
        }
      }

      "multi" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Remove, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob, carl)), List(State.Normal)))
          expect {
            one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(
              new single.Remove(alice, FOLLOWS, bob, Time.now.inMillis, Time.now),
              new single.Remove(alice, FOLLOWS, carl, Time.now.inMillis, Time.now))))
          }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Remove, new QueryTerm(alice, FOLLOWS, false, Some(List[Long](bob, carl)), List(State.Normal)))
          expect {
            one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(
              new single.Remove(bob, FOLLOWS, alice, Time.now.inMillis, Time.now),
              new single.Remove(carl, FOLLOWS, alice, Time.now.inMillis, Time.now))
            ))
          }
          executeCompiler(program)
        }
      }
    }

    "compile archive operations" in {
      "single" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Archive, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(single.Archive(alice, FOLLOWS, bob, Time.now.inMillis, Time.now)))) }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Archive, new QueryTerm(alice, FOLLOWS, false, Some(List[Long](bob)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(single.Archive(bob, FOLLOWS, alice, Time.now.inMillis, Time.now)))) }
          executeCompiler(program)
        }
      }

      "aggregate" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Archive, new QueryTerm(alice, FOLLOWS, true, None, List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(multi.Archive(alice, FOLLOWS, Direction.Forward, Time.now, Priority.Low)))) }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Archive, new QueryTerm(alice, FOLLOWS, false, None, List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(multi.Archive(alice, FOLLOWS, Direction.Backward, Time.now, Priority.Low)))) }
          executeCompiler(program)
        }
      }

      "multi" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Archive, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob, carl)), List(State.Normal)))
          expect {
            one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(
              single.Archive(alice, FOLLOWS, bob, Time.now.inMillis, Time.now),
              single.Archive(alice, FOLLOWS, carl, Time.now.inMillis, Time.now))))
          }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Archive, new QueryTerm(alice, FOLLOWS, false, Some(List[Long](bob, carl)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(
            single.Archive(bob, FOLLOWS, alice, Time.now.inMillis, Time.now),
            single.Archive(carl, FOLLOWS, alice, Time.now.inMillis, Time.now))))
          }
          executeCompiler(program)
        }
      }
    }

    "negate" >> {
      "single" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Negate, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(single.Negate(alice, FOLLOWS, bob, Time.now.inMillis, Time.now)))) }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Negate, new QueryTerm(alice, FOLLOWS, false, Some(List[Long](bob)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(single.Negate(bob, FOLLOWS, alice, Time.now.inMillis, Time.now)))) }
          executeCompiler(program)
        }
      }

      "aggregate" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Negate, new QueryTerm(alice, FOLLOWS, true, None, List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(multi.Negate(alice, FOLLOWS, Direction.Forward, Time.now, Priority.Low)))) }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Negate, new QueryTerm(alice, FOLLOWS, false, None, List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(multi.Negate(alice, FOLLOWS, Direction.Backward, Time.now, Priority.Low)))) }
          executeCompiler(program)
        }
      }

      "multi" >> {
        "forward" >> {
          val program = termToProgram(ExecuteOperationType.Negate, new QueryTerm(alice, FOLLOWS, true, Some(List[Long](bob, carl)), List(State.Normal)))
          expect {
            one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(
              single.Negate(alice, FOLLOWS, bob, Time.now.inMillis, Time.now),
              single.Negate(alice, FOLLOWS, carl, Time.now.inMillis, Time.now))))
          }
          executeCompiler(program)
        }

        "backward" >> {
          val program = termToProgram(ExecuteOperationType.Negate, new QueryTerm(alice, FOLLOWS, false, Some(List[Long](bob, carl)), List(State.Normal)))
          expect { one(scheduler).apply(Priority.Low.id, new SchedulableWithTasks(List(
            single.Negate(bob, FOLLOWS, alice, Time.now.inMillis, Time.now),
            single.Negate(carl, FOLLOWS, alice, Time.now.inMillis, Time.now))))
          }
          executeCompiler(program)
        }
      }
    }
  }
}
