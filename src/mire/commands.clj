(ns mire.commands
  (:require [clojure.string :as str]
            [mire.rooms :as rooms]
            [mire.player :as player]))

(defn- move-between-refs
  "Move one instance of obj between from and to. Must call in a transaction."
  [obj from to]
  (alter from disj obj)
  (alter to conj obj))

;; Command functions

(defn look
  "Get a description of the surrounding environs and its contents."
  []
  (str "You're in the " (:name @player/*current-room*) (:desc @player/*current-room*)
       player/eol "Exits: " (keys @(:exits @player/*current-room*)) player/eol
       (str/join player/eol (map #(str "There is " % " here." player/eol)
                           @(:items @player/*current-room*)))
       (if (empty? (disj @(:inhabitants @player/*current-room*) player/*name*))
          (str "You are alone in the room." player/eol)
          (str "Players: " (str/join ", " (disj @(:inhabitants @player/*current-room*) player/*name*)) "." player/eol)
       )
  )
)

(defn move
  "\"♬ We gotta get out of this place... ♪\" Give a direction."
  [direction]
  (dosync
   (let [target ((keyword direction) @(:exits @player/*current-room*))]
     (if target
       (do
         (move-between-refs player/*name*
                            (:inhabitants @player/*current-room*)
                            (:inhabitants @target))
         (ref-set player/*current-room* @target)
         (look))
       (str "You can't go that way." player/eol)))))

(defn check-set
  "check status of the item set!"
  [item]
  (dosync   
	
   (cond
   (or (= (player/carrying? item) :wood-sword) (= (player/carrying? item) :wood-armor))
         (do (if (and (@player/*inventory* :wood-sword ) (@player/*inventory* :wood-armor ) )
		      (if (not (@player/*sets* :wood ))
					(do 
						(alter player/*sets* conj :wood)
						(player/add-points 200)
						(str "That's full wood set! +200 points" player/eol )
					)
					(str "That's full wood set!"))
              (str "One more thing to go in wood set!" player/eol )))
	
	(or (= (player/carrying? item) :banana) (= (player/carrying? item) :kiwi) (= (player/carrying? item) :apple))
			(do (if (and (@player/*inventory* :banana) (@player/*inventory* :kiwi ) (@player/*inventory* :apple))
				(if (not (@player/*sets* :fruit ))
					(do 
						(alter player/*sets* conj :fruit)
						(commute player/health assoc player/*name* (+ (@player/health player/*name*) 15))
						(str "That's full fruit set! +15 health points" player/eol )
					)
					(str "That's full fruit set!"))
				
              (str "There are 3 items in fruit set! Find them all!" player/eol )))
    (or (= (player/carrying? item) :ruby) (= (player/carrying? item) :emerald) (= (player/carrying? item) :diamond))
			(do (if (and (@player/*inventory* :ruby) (@player/*inventory* :emerald ) (@player/*inventory* :diamond))
				(if (not (@player/*sets* :philosophers-stone ))
					(do 
						(alter player/*sets* conj :philosophers-stone)
						(player/add-points 50000)
						(str "That's full philosophers' stone! +50000 points!!!! YOU WIN!!!!" player/eol )
					)
					(str "That's full philosophers' stone!"))
				
              (str "Find 3 stones... and you'll become more powerfull!" player/eol )))
	:else (str "sorry... there is no set for this item :(" player/eol )
	
   )
  ))	   
	   
	   
	   
	   
	   
(defn grab
  "Pick something up."
  [thing]
  (dosync
    (if (rooms/room-contains? @player/*current-room* thing)
      (if (= (compare thing "keys") 0)
        (do 
          (.set player/*keys-count* (inc (.get player/*keys-count*)))
          (alter (:items @player/*current-room*) disj :keys)
          (str "You picked up keys." player/eol))
        (do 
          (move-between-refs (keyword thing)
                            (:items @player/*current-room*)
                            player/*inventory*)
		  (print (check-set thing))					
          (str "You picked up the " thing "." player/eol)))
     (str "There isn't any " thing " here." player/eol))))

(defn discard
  "Put something down that you're carrying."
  [thing]
  (dosync
    (if (= (compare thing "keys") 0)
      (if (> (.get player/*keys-count*) 0)  
        (do
          (.set player/*keys-count* (dec (.get player/*keys-count*)))
          (alter (:items @player/*current-room*) conj :keys)
          (str "You dropped keys." player/eol))
        "You dont have any keys.")
      (do 
        (if (player/carrying? thing)
          (do 
            (move-between-refs (keyword thing)
                            player/*inventory*
                            (:items @player/*current-room*))
            (str "You dropped the " thing "." player/eol))
          (str "You're not carrying a " thing "." player/eol))))))

(defn inventory
  "See what you've got."
  []
  (str "You are carrying:" player/eol
       (str/join player/eol (seq @player/*inventory*))
       "You have " (.get player/*keys-count*) " keys." player/eol))

(defn detect
  "If you have the detector, you can see which room an item is in."
  [item]
  (if (@player/*inventory* :detector)
    (if-let [room (first (filter #((:items %) (keyword item))
                                 (vals @rooms/rooms)))]
      (str item " is in " (:name room))
      (str item " is not in any room." player/eol))
    (str "You need to be carrying the detector for that." player/eol)))

(defn say
  "Say something out loud so everyone in the room can hear."
  [& words]
  (let [message (str/join " " words)]
    (doseq [inhabitant (disj @(:inhabitants @player/*current-room*)
                             player/*name*)]
      (binding [*out* (player/streams inhabitant)]
        (println message)
        (println)
        (println player/prompt)))
    (str "You said " message player/eol)))

(defn help
  "Show available commands and what they do."
  []
  (str
  (str/join player/eol (map #(str (key %) ": " (:doc (meta (val %))))
                      (dissoc (ns-publics 'mire.commands)
                              'execute 'commands))) player/eol))

(defn score
  "Show players score."
  []
  (str "Scoreboard" player/eol
  (str/join player/eol (map #(str (key %) ": " (val %)) (reverse (sort-by #(val %) @player/scores)))) player/eol))

(defn get-points
  "MORE POINTS!!!!!!!"
  []
  (player/add-points 25000)
  (str "MORE POINTS!!!!!!!" player/eol))

(defn attack 
  "Attack other player"
  [target-number]
  (if-let [target (nth (vec (disj @(:inhabitants @player/*current-room*) player/*name*)) (Integer/parseInt target-number))]
    (case (player/attack target)
      2 (str "You killed " target "." player/eol)
      1 (do 
          (binding [*out* (player/streams target)]
            (println)
            (println (str "You was attacked by " player/*name* "."))
            (println (str "You hp is " (@player/health target) "."))
            (println)
            (print player/prompt) (flush))
          (str "You attacked " target "." player/eol 
            target " counterattack." player/eol
            "You hp is " (@player/health player/*name*) "." player/eol))
      0 (str target " isn't here." player/eol))
    (str "There is not " target-number "th player here")))

(defn status
  "Player status"
  []
  (str 
    "You health: " (@player/health player/*name*) "." player/eol
    "You score: " (@player/scores player/*name*) "." player/eol))
	
	


	

;; Command data

(def commands {"move" move,
               "north" (fn [] (move :north)),
               "south" (fn [] (move :south)),
               "east" (fn [] (move :east)),
               "west" (fn [] (move :west)),
               "grab" grab
               "discard" discard
               "inventory" inventory
               "detect" detect
               "look" look
               "say" say
               "help" help
               "score" score
               "hesoyam" get-points
               "attack" attack
               "status" status
			   "check-set" check-set})

;; Command handling

(defn execute
  "Execute a command that is passed to us."
  [input]
  (try 
    (let [[command & args] (.split input " +")]
      (apply (commands command) args))
    (catch Exception e
      (.printStackTrace e (new java.io.PrintWriter *err*))
      "You can't do that!")))

