db = db.getSiblingDB("sample_mflix")


const nCopies = 172 
const multiple_docs = [  { $set : { copy : { $range : [ 0, nCopies ]}}},
{ $unwind :  "$copy" },
{ $set : { _id: "$$REMOVE"}}
]

const small_rand = {$subtract : [ {$multiply :  [ { $rand : {} }, 0.0000001]}, 0.00000005 ]}
const vary_vector = { $map : {  input: "$plot_embedding" , in: { $add : [ "$$this",small_rand ]} }}
const do_vary = { $set : { plot_embedding :  { $cond : { if: { $eq: ["$copy",0]}, then: "$plot_embedding", else: vary_vector }}}}
const movies_with_embed = { $out : "movies_with_embed"}
db.embedded_movies.aggregate([...multiple_docs, do_vary,movies_with_embed])


//Make Split version
db.movies_with_embed.aggregate([ {$project: {plot_embedding:1}},{$out:"embeddings_only"}])
db.movies_with_embed.aggregate([ {$project: {plot_embedding:0}},{$out:"movies_only"}])