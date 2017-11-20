// Compiled by ClojureScript 1.9.908 {}
goog.provide('reagent.debug');
goog.require('cljs.core');
reagent.debug.has_console = typeof console !== 'undefined';
reagent.debug.tracking = false;
if(typeof reagent.debug.warnings !== 'undefined'){
} else {
reagent.debug.warnings = cljs.core.atom.call(null,null);
}
if(typeof reagent.debug.track_console !== 'undefined'){
} else {
reagent.debug.track_console = (function (){var o = ({});
o.warn = ((function (o){
return (function() { 
var G__32545__delegate = function (args){
return cljs.core.swap_BANG_.call(null,reagent.debug.warnings,cljs.core.update_in,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"warn","warn",-436710552)], null),cljs.core.conj,cljs.core.apply.call(null,cljs.core.str,args));
};
var G__32545 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__32546__i = 0, G__32546__a = new Array(arguments.length -  0);
while (G__32546__i < G__32546__a.length) {G__32546__a[G__32546__i] = arguments[G__32546__i + 0]; ++G__32546__i;}
  args = new cljs.core.IndexedSeq(G__32546__a,0,null);
} 
return G__32545__delegate.call(this,args);};
G__32545.cljs$lang$maxFixedArity = 0;
G__32545.cljs$lang$applyTo = (function (arglist__32547){
var args = cljs.core.seq(arglist__32547);
return G__32545__delegate(args);
});
G__32545.cljs$core$IFn$_invoke$arity$variadic = G__32545__delegate;
return G__32545;
})()
;})(o))
;

o.error = ((function (o){
return (function() { 
var G__32548__delegate = function (args){
return cljs.core.swap_BANG_.call(null,reagent.debug.warnings,cljs.core.update_in,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"error","error",-978969032)], null),cljs.core.conj,cljs.core.apply.call(null,cljs.core.str,args));
};
var G__32548 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__32549__i = 0, G__32549__a = new Array(arguments.length -  0);
while (G__32549__i < G__32549__a.length) {G__32549__a[G__32549__i] = arguments[G__32549__i + 0]; ++G__32549__i;}
  args = new cljs.core.IndexedSeq(G__32549__a,0,null);
} 
return G__32548__delegate.call(this,args);};
G__32548.cljs$lang$maxFixedArity = 0;
G__32548.cljs$lang$applyTo = (function (arglist__32550){
var args = cljs.core.seq(arglist__32550);
return G__32548__delegate(args);
});
G__32548.cljs$core$IFn$_invoke$arity$variadic = G__32548__delegate;
return G__32548;
})()
;})(o))
;

return o;
})();
}
reagent.debug.track_warnings = (function reagent$debug$track_warnings(f){
reagent.debug.tracking = true;

cljs.core.reset_BANG_.call(null,reagent.debug.warnings,null);

f.call(null);

var warns = cljs.core.deref.call(null,reagent.debug.warnings);
cljs.core.reset_BANG_.call(null,reagent.debug.warnings,null);

reagent.debug.tracking = false;

return warns;
});

//# sourceMappingURL=debug.js.map?rel=1510137267533
