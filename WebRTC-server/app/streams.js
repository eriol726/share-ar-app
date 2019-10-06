module.exports = function() {
  /**
   * available streams 
   * the id value is considered unique (provided by socket.io)
   */
  var streamList = [];

  /**
   * Stream object
   */
  var Stream = function(id, name, type, payload) {
    this.name = name;
    this.id = id;
    this.type = type;
    this.payload = payload
  }

  return {
    addStream : function(id, name, type, payload) {
      console.log("addStream: ", id);
      var stream = new Stream(id, name, type, payload);
      var exists = streamList.some(item =>{
        return item.id === id;
      }) 

      if(!exists){
        streamList.push(stream);
      }

      
    },

    removeStream : function(id) {
      var index = 0;
      while(index < streamList.length && streamList[index].id != id){
        index++;
      }

      var index2 = streamList.findIndex(item => item.id === id);
      streamList.splice(index, 1);
    },

    // update function
    update : function(id, name) {
      var stream = streamList.find(function(element, i, array) {
        return element.id == id;
      });
      stream.name = name;
    },

    update1 : function(id, name, type, payload) {
      var stream = streamList.find(function(element, i, array) {
        return element.id == id;
      });

      if(stream){
        stream.name = name;
        stream.type = type;
        stream.payload = payload;
      }
      
    },

    getStreams : function() {
      return streamList;
    }
  }
};
