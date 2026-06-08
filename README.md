
I agree with all of that.

- For Decision 1: How long should we remember a key? 
  1. Well what we are concerned about is making sure that the map does not eat all of our memory while the app is running, since this is an in-memory store, its lifecycle is basically how long the application runs

- For Decision 2: How do we enforce the limit?
  Since it is an in-memory store, Our options include:
  1. Time-Based Expiry and Size-Based Cap: ach entry gets a timestamp. A background task periodically scans the map and removes entries older than the specified time limit. Additionally, we can set a maximum size for the map, and when that limit is reached, we can remove the oldest entries to make room for new ones.
  2. Also, if there will be no problem, we can use a simple LRU (Least Recently Used) eviction policy, which will remove the least recently accessed key when the limit is reached. This way we can ensure that we are not keeping keys that are not being used, while still allowing for a reasonable amount of keys to be stored in memory.

- For Decision3: Who triggers the cleanup process?
  Since we are working on an in-memory store, we will just use the:
  1. The Scheduled Cleanup: We can have a background thread or task that runs at regular intervals (e.g., every minute) to check for expired entries and remove them from the map. This way, we can ensure that the cleanup process is automated and does not rely on user interactions.
  2. On-Demand Cleanup: We can also trigger the cleanup process whenever a new entry is added to the map. Before adding a new entry, we can check if the map has reached its size limit or if there are any expired entries, and perform the cleanup accordingly. This way, we can ensure that the map does not exceed its limits while still allowing for new entries to be added. This is the On-write cleanup strategy

- For Decision 4: Should the retention window be configurable?
  1. Yes, the retention window should be configurable to allow for flexibility based on the application's needs. Different applications may have different requirements for how long they want to retain keys in memory, so providing a configurable option allows developers to tailor the behavior of the map to their specific use case. This can be achieved by allowing users to set the retention time and size limits through configuration settings or environment variables.
  But the host application devs need to understand the type of store they are using, so they know how to add configurations that don't bite later.

In summary, we will implement a time-based expiry and size-based cap for our in-memory map, with a scheduled cleanup process that runs at regular intervals. The retention window will be configurable to allow for flexibility based on the application's needs. This approach will help us manage memory effectively while ensuring that we are not keeping unnecessary keys in memory.