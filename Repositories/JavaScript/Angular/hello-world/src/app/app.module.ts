import { BrowserModule } from '@angular/platform-browser';
import { FormsModule, COMPOSITION_BUFFER_MODE } from '@angular/forms';
import { NgModule } from '@angular/core';

import { AppComponent } from './app.component';
import { HelloComponent } from './hello/hello.component';
import { LangSelectorComponent } from './lang-selector/lang-selector.component';
import { Hello2Component } from './hello2/hello2.component';
import { Hello3Component } from './hello3/hello3.component';

@NgModule({
  declarations: [
    AppComponent,
    HelloComponent,
    LangSelectorComponent,
    Hello2Component,
    Hello3Component
  ],
  imports: [
    BrowserModule,
    FormsModule
  ],
  providers: [{provide: COMPOSITION_BUFFER_MODE, useValue: false}],
  bootstrap: [AppComponent]
})
export class AppModule { }
